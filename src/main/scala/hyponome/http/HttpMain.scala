package hyponome.http

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, RemoteAddress, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import java.lang.SuppressWarnings
import java.net.{InetAddress, URI}
import java.nio.file.{FileSystem, FileSystems, Path}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}
import slick.driver.H2Driver.api.Database
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.actor._
import hyponome.core._
import hyponome.http.Marshallers._

@SuppressWarnings(Array(
  "org.brianmckenna.wartremover.warts.Any",
  "org.brianmckenna.wartremover.warts.AsInstanceOf",
  "org.brianmckenna.wartremover.warts.IsInstanceOf",
  "org.brianmckenna.wartremover.warts.Nothing"
))
object HttpMain extends App {
  implicit val system: ActorSystem = ActorSystem("Hyponome")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  val fs: FileSystem  = FileSystems.getDefault()
  val config: Config  = ConfigFactory.parseFile(new java.io.File("hyponome.conf"))
  val store: Path     = fs.getPath(config.getString("file-store.path"))
  val db: DatabaseDef = Database.forConfig("h2")
  val uploadKey       = "file"
  val hostname        = InetAddress.getLocalHost().getHostName()
  val port            = 3000

  val recActor = system.actorOf(Props(new Receptionist(db, store)))
  val askActor = system.actorOf(AskActor.props(recActor))

  val initActor = system.actorOf(AskActor.props(recActor))
  val init: Future[Any] = ask(initActor, Create)
  init onComplete {
    case msg =>
      println(msg)
      system.stop(initActor)
  }

  def handlePostObjects(a: ActorRef, r: RemoteAddress, i: FileInfo, f: java.io.File): Route = {
    def makeAddition(i: FileInfo, f: java.io.File, r: RemoteAddress): Addition = {
      val p: Path = f.toPath
      val h: SHA256Hash = getSHA256Hash(p)
      Addition(p, h, i.fileName, i.contentType.toString, f.length, r.toOption)
    }
    def response(f: Addition, s: Status): Response = {
      val uri = new URI(s"http://$hostname:$port/objects/${f.hash}")
      Response(s, uri, f.hash, f.name, f.contentType, f.length, f.remoteAddress)
    }
    val responseFuture: Future[AdditionResponse] =
      ask(a, makeAddition(i, f, r)).mapTo[AdditionResponse]
    onSuccess(responseFuture) {
      case AdditionAck(a)     => complete { response(a, Created) }
      case PreviouslyAdded(a) => complete { response(a, Exists)  }
      case AdditionFail(a)    => complete { HttpResponse(StatusCodes.InternalServerError)}
    }
  }

  def handleGetObjects(a: ActorRef, h: String): Route = {
    val responseFuture: Future[Option[Path]] =
      ask(a, FindFile(SHA256Hash(h)))
        .mapTo[Result]
        .map(f => f.file)
    onSuccess(responseFuture) {
      case Some(f) => getFromFile(f.toFile)
      case None    => reject
    }
  }

  def objectsRoute(a: ActorRef, u: String): Route = {
    pathPrefix("objects") {
      pathEnd {
        post {
          extractClientIP { ip =>
            uploadedFile(u) { case (metadata, file) =>
              handlePostObjects(a, ip, metadata, file)
            }
          }
        }
      } ~
      path(Rest) { hash =>
        get {
          handleGetObjects(a, hash)
        }
      }
    }
  }

  val route = objectsRoute(askActor, uploadKey)
  val bindingFuture = Http().bindAndHandle(route, hostname, port)

  println(s"Server online at http://$hostname:$port/\nPress RETURN to stop...")

  val _ = StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
