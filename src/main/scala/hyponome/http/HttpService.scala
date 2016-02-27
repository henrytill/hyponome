package hyponome.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
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
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import slick.driver.H2Driver.api.Database

import hyponome.actor._
import hyponome.core._
import hyponome.http.Marshallers._

@SuppressWarnings(Array("org.brianmckenna.wartremover.warts.ExplicitImplicitTypes"))
final class HttpService(
  conf: HyponomeConfig,
  system: Option[ActorSystem],
  recActor: Option[ActorRef],
  askActor: Option[ActorRef],
  bindingFuture: Option[Future[ServerBinding]]
)(implicit ec: ExecutionContext) {

  implicit val timeout: Timeout = Timeout(5.seconds)
  val logger: Logger  = LoggerFactory.getLogger(classOf[HttpService])
  val HyponomeConfig(db, store, hostname, port, uploadKey) = conf

  def handleFailure(ex: Throwable): (StatusCodes.ServerError, String) =
    (StatusCodes.InternalServerError, ex.getMessage)

  def handlePostObjects(a: ActorRef, r: RemoteAddress, i: FileInfo, f: java.io.File): Route = {
    def makeAddition(i: FileInfo, f: java.io.File, r: RemoteAddress): Future[Addition] = {
      val p: Path = f.toPath
      getSHA256Hash(p).map { h =>
        Addition(p, h, i.fileName, i.contentType.toString, f.length, r.toOption)
      }
    }
    def response(f: Addition, s: Status): Response = {
      val uri = new URI(s"http://$hostname:$port/objects/${f.hash}")
      Response(s, uri, f.hash, f.name, f.contentType, f.length, f.remoteAddress)
    }
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any"))
    val responseFuture: Future[AdditionResponse] =
      makeAddition(i, f, r)
        .flatMap(ask(a, _))
        .mapTo[AdditionResponse]
    onComplete(responseFuture) {
      case Success(AdditionAck(a))      => complete { response(a, Created) }
      case Success(PreviouslyAdded(a))  => complete { response(a, Exists)  }
      case Success(AdditionFail(a, ex)) => complete { handleFailure(ex)    }
      case Failure(ex)                  => complete { handleFailure(ex)    }
    }
  }

  def handleGetObjects(a: ActorRef, h: String): Route = {
    val responseFuture: Future[Option[java.io.File]] =
      ask(a, FindFile(SHA256Hash(h)))
        .mapTo[Result]
        .map(_.file)
        .map(_.map(_.toFile))
    onComplete(responseFuture) {
      case Success(Some(f)) => getFromFile(f)
      case Success(None)    => reject
      case Failure(ex)      => complete { handleFailure(ex) }
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

  def start(): HttpService = {
    bindingFuture match {
      case Some(_) => this
      case None    =>
        logger.info(s"Starting server at http://$hostname:$port/")
        implicit val sys: ActorSystem       = ActorSystem("Hyponome")
        implicit val mat: ActorMaterializer = ActorMaterializer()
        val recActor: ActorRef = sys.actorOf(Receptionist.props(db, store))
        val askActor: ActorRef = sys.actorOf(AskActor.props(recActor))
        val route:    Route    = objectsRoute(askActor, uploadKey)
        new HttpService(
          conf,
          Some(sys),
          Some(recActor),
          Some(askActor),
          Some(Http().bindAndHandle(route, hostname, port))
        )(sys.dispatcher)
    }
  }

  def stop(): HttpService = {
    bindingFuture match {
      case None     => this
      case Some(bf) =>
        logger.info(s"Stopping server at http://$hostname:$port/")
        bf.flatMap(_.unbind).onComplete(_ => system.get.terminate())
        HttpService(conf)
    }
  }
}

object HttpService {

  private val fs: FileSystem = FileSystems.getDefault()

  private val configFile: Path = fs.getPath("hyponome.conf")

  private val config: Config = ConfigFactory.parseFile(configFile.toFile)

  val defaultConfig = HyponomeConfig(
    Database.forConfig("h2"),
    fs.getPath(config.getString("file-store.path")),
    InetAddress.getLocalHost().getHostName(),
    3000,
    "file"
  )

  def apply(): HttpService = {
    apply(defaultConfig)
  }

  def apply(conf: HyponomeConfig): HttpService = {
    new HttpService(conf, None, None, None, None)(ExecutionContext.global)
  }
}
