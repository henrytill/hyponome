/*
 * Copyright 2016 Henry Till
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hyponome.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpResponse, RemoteAddress, StatusCodes}
import akka.http.scaladsl.model.headers._
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
import java.sql.Timestamp
import org.slf4j.{Logger, LoggerFactory}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import slick.driver.H2Driver.api.Database
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.actor._
import hyponome.core._
import hyponome.http.Marshallers._

@SuppressWarnings(Array(
  "org.brianmckenna.wartremover.warts.DefaultArguments",
  "org.brianmckenna.wartremover.warts.ExplicitImplicitTypes"
))
final class HttpService(
  conf: HyponomeConfig,
  system: Option[ActorSystem] = None,
  recActor: Option[ActorRef] = None,
  askActor: Option[ActorRef] = None,
  bindingFuture: Option[Future[ServerBinding]] = None
)(implicit ec: ExecutionContext = ExecutionContext.global) {

  implicit val timeout: Timeout = Timeout(5.seconds)
  val logger: Logger  = LoggerFactory.getLogger(classOf[HttpService])
  val HyponomeConfig(db, store, hostname, port, uploadKey) = conf

  def handleFailure(ex: Throwable): (StatusCodes.ServerError, String) =
    (StatusCodes.InternalServerError, ex.getMessage)

  def handlePostObjects(a: ActorRef, r: RemoteAddress, i: FileInfo, f: java.io.File): Route = {
    def makePost(i: FileInfo, f: java.io.File, r: RemoteAddress): Future[Post] = {
      val name = if (i.fileName == "-") None else Some(i.fileName)
      val p: Path = f.toPath
      getSHA256Hash(p).map { h =>
        Post(hostname, port, p, h, name, i.contentType.toString, f.length, r.toOption)
      }
    }
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any"))
    val responseFuture: Future[PostResponse] =
      makePost(i, f, r)
        .flatMap(ask(a, _))
        .mapTo[PostResponse]
    onComplete(responseFuture) {
      case Success(PostAck(p: Posted)) => complete { p }
      case Success(PostFail(ex))       => complete { handleFailure(ex) }
      case Failure(ex)                 => complete { handleFailure(ex) }
    }
  }

  def handleQueryObjects(a: ActorRef): Route =
    parameters('hash.?, 'name.?, 'remoteAddress.?, 'txLo.?, 'txHi.?, 'timeLo.?, 'timeHi.?, 'sortBy.?, 'sortOrder.?) {
      (hash, name, remoteAddress, txLo, txHi, timeLo, timeHi, sortBy, sortOrder) => {
        val q = DBQuery(
          hash.map(SHA256Hash(_)),
          name,
          remoteAddress.map(InetAddress.getByName(_)),
          txLo.map(_.toLong),
          txHi.map(_.toLong),
          timeLo.map(Timestamp.valueOf(_)),
          timeHi.map(Timestamp.valueOf(_)),
          sortBy match {
            case Some("address") => Address
            case Some("name")    => Name
            case Some("time")    => Time
            case _               => Tx
          },
          sortOrder match {
            case Some("desc") => Descending
            case _            => Ascending
          }
        )
        val responseFuture: Future[Seq[DBQueryResponse]] =
          ask(a, q).mapTo[Seq[DBQueryResponse]]
        onComplete(responseFuture) {
          case Success(rs: Seq[DBQueryResponse]) => complete { rs }
          case Failure(ex)                       => complete { handleFailure(ex) }
        }
      }
    }

  def handleRedirectObject(a: ActorRef, h: SHA256Hash): Route = {
    val responseFuture: Future[Result] = ask(a, h).mapTo[Result]
    onComplete(responseFuture) {
      case Success(Result(Some(_),    Some(name))) => complete { Redirect(getURI(hostname, port, h, Some(name))) }
      case Success(Result(Some(file), None))       => getFromFile(file.toFile)
      case Success(Result(None,       _))          => reject
      case Failure(ex)                             => complete { handleFailure(ex) }
    }
  }

  def handleGetObject(a: ActorRef, h: SHA256Hash, n: String): Route = {
    val responseFuture: Future[Result] = ask(a, h).mapTo[Result]
    onComplete(responseFuture) {
      case Success(Result(Some(file), Some(name))) if name == n => getFromFile(file.toFile)
      case Success(Result(_,          _))                       => reject
      case Failure(ex)                                          => complete { handleFailure(ex) }
    }
  }

  def handleDeleteObject(a: ActorRef, h: SHA256Hash, r: RemoteAddress): Route = {
    val responseFuture: Future[DeleteResponse] = ask(a, Delete(h, r.toOption)).mapTo[DeleteResponse]
    onComplete(responseFuture) {
      case Success(DeleteAck(_, s: DeleteStatus)) => complete { s }
      case Success(DeleteFail(_, ex))             => complete { handleFailure(ex) }
      case Failure(ex)                            => complete { handleFailure(ex) }
    }
  }

  def objectsRoute(a: ActorRef, u: String): Route = {
    respondWithHeader(`Access-Control-Allow-Origin`.*) {
      pathPrefix("objects") {
        pathEnd {
          post {
            extractClientIP { ip =>
              uploadedFile(u) { case (metadata, file) =>
                handlePostObjects(a, ip, metadata, file)
              }
            }
          } ~
          get {
            handleQueryObjects(a)
          }
        } ~
        pathPrefix(Segment) { hash =>
          val obj = SHA256Hash(hash)
          pathEnd {
            get {
              handleRedirectObject(a, obj)
            } ~
            delete {
              extractClientIP { ip => handleDeleteObject(a, obj, ip) }
            }
          } ~
          path(Segment) { name =>
            get {
              handleGetObject(a, obj, name)
            }
          }
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
        val ctlActor: ActorRef = sys.actorOf(Controller.props(db, store))
        val askActor: ActorRef = sys.actorOf(AskActor.props(ctlActor))
        val route:    Route    = objectsRoute(askActor, uploadKey)
        new HttpService(
          conf,
          Some(sys),
          Some(ctlActor),
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
        new HttpService(conf)
    }
  }
}

object HttpService {

  private val dbConfig: Function0[DatabaseDef] = { () => Database.forConfig("h2") }

  private val defaults: Map[String, String] = Map(
    "file-store.path" -> "store",
    "server.hostname" -> "localhost",
    "server.port" -> "3000",
    "upload.key" -> "file"
  );

  private val fs: FileSystem = FileSystems.getDefault()

  private val configFile: java.io.File = fs.getPath("hyponome.conf").toFile

  private val configDefault: Config = ConfigFactory.parseMap(defaults.asJava)

  val config: Config = ConfigFactory.parseFile(configFile).withFallback(configDefault)

  val defaultConfig = HyponomeConfig(
    dbConfig,
    fs.getPath(config.getString("file-store.path")),
    config.getString("server.hostname"),
    config.getInt("server.port"),
    config.getString("upload.key")
  )

  def apply(): HttpService = new HttpService(defaultConfig)

  def apply(conf: HyponomeConfig): HttpService = new HttpService(conf)
}
