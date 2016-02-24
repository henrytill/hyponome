package hyponome.http

// import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import hyponome.actor._
import hyponome.core._
import java.lang.SuppressWarnings
import java.net.InetAddress
import java.nio.file.{FileSystem, FileSystems, Path}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import slick.driver.H2Driver.api.Database
import slick.driver.H2Driver.backend.DatabaseDef

@SuppressWarnings(Array(
  "org.brianmckenna.wartremover.warts.Any",
  "org.brianmckenna.wartremover.warts.Nothing"
))
object HttpMain extends App {
  implicit val system: ActorSystem = ActorSystem("my-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val fs: FileSystem  = FileSystems.getDefault()
  val config: Config  = ConfigFactory.parseFile(new java.io.File("hyponome.conf"))
  val store: Path     = fs.getPath(config.getString("file-store.path"))
  val db: DatabaseDef = Database.forConfig("h2")
  val uploadKey       = "file"

  val dbActor   = system.actorOf(DBActor.props(db))
  val fileActor = system.actorOf(FileActor.props(store))
  val askActor  = system.actorOf(AskActor.props(dbActor, fileActor))

  implicit val timeout: Timeout = Timeout(5.seconds)
  val init: Future[Any] = ask(askActor, AskActor.Create)
  init onSuccess {
    case _ => println(init.value)
  }

  val objectsRoute: Route = {
    path("objects") {
      post {
        extractClientIP { ip =>
          uploadedFile(uploadKey) {
            case (metadata, file) =>
              val filePath: Path = file.toPath
              val FileInfo(_, name, contentType) = metadata
              val add: Addition = Addition(
                filePath,
                getSHA256Hash(filePath),
                name,
                contentType.toString,
                file.length,
                ip.toOption
              )
              val resFuture: Future[Any] = ask(askActor, add)
              resFuture onComplete {
                case _ => println(resFuture.value)
              }
              complete("foo")
          }
        }
      }
    } ~
    pathPrefix("objects" / Segment) { hash =>
      get {
        complete(hash)
      }
    }
  }

  val route = objectsRoute
  val (hostname, port) = ("thalassa.home", 3000)
  val bindingFuture = Http().bindAndHandle(route, hostname, port)

  println(s"Server online at http://$hostname:$port/\nPress RETURN to stop...")

  val _ = StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
