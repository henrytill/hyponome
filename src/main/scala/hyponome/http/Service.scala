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

import argonaut._
import Argonaut._
import org.http4s._
import org.http4s.dsl.{Created => _, NotFound => HNotFound, _}
import org.http4s.multipart._
import java.net.InetAddress
import java.nio.file.{Files => JFiles, Path => JPath}
import java.sql.Timestamp
import scala.concurrent.ExecutionContext
import scalaz.Nondeterminism
import scalaz.stream.io.fileChunkW
import scalaz.concurrent.Task
import hyponome._
import hyponome.config.ServiceConfig
import hyponome.db.HyponomeDB
import hyponome.file._
import hyponome.util._
import JsonProtocol._

final class Service(cfg: ServiceConfig, db: HyponomeDB, store: LocalFileStore)(implicit ec: ExecutionContext) {

  def createTmpDir(): JPath = JFiles.createTempDirectory("hyponome")
  val tmpDir: JPath         = createTmpDir()

  def handlePart(r: Request, p: Part)(implicit ec: ExecutionContext): Task[Option[Added]] = {
    val Part(hs, body) = p
    val parameters: Option[Map[String, String]] = hs.get(headers.`Content-Disposition`).map(_.parameters)
    // get the "name" parameter
    parameters.flatMap(_.get("name")) match {
      // check if it matches the uploadKey specified in the config
      case Some(x) if x == cfg.uploadKey =>
        // if it does, get the filename, content-type, and address from request/headers
        val filename: Option[String] =
          parameters
            .flatMap(_.get("filename"))
            .flatMap((x: String) => if (x != "-") Some(x) else None)
        val contentType: String =
          hs.get(headers.`Content-Type`)
            .map(_.mediaType)
            .map((m: MediaType) => s"${m.mainType}/${m.subType}")
            .getOrElse("application/octet-stream")
        val inetAddress: Option[InetAddress] = r.remote.map(_.getAddress())
        // create a path where the upload will be temporarily copied
        val tempFilePath: JPath = tmpDir.resolve(s"${randomUUID}.tmp")
        // for comprehension helper functions
        def bodyToFile(b: EntityBody, p: JPath): Task[JPath] = {
          b.to(fileChunkW(p.toFile.toString)).run.map((_: Unit) => p)
        }
        def createAdd(hash: SHA256Hash, file: JPath, filename: Option[String]): Task[Add] =
          Task.now {
            Add(cfg.hostname, cfg.port, file, hash, filename, contentType, file.toFile.length, inetAddress)
          }
        def addToDB(a: Add): Task[AddStatus] =
          futureToTask {
            db.addFile(a)
          }
        def addToFileStore(a: Add): PartialFunction[AddStatus, Task[Added]] = {
          case Exists => futureToTask(db.findFile(a.hash)).flatMap {
            case Some(f) => Task.now(Added(a.mergeWithFile(f), Exists))
            case None    => Task.fail(new RuntimeException)
          }
          case Created => store.copyToStore(a).map {
            case Exists  => Added(a, Exists)
            case Created => Added(a, Created)
          }
        }
        // add the file to the store and yield a Task[Option[Added]]
        for {
          p <- bodyToFile(body, tempFilePath)
          h <- getSHA256Hash(p)
          x <- createAdd(h, p, filename)
          y <- addToDB(x)
          z <- addToFileStore(x)(y)
        } yield Some(z)
      case _ =>
        // otherwise, return a Task[None]
        Task.now(None)
    }
  }

  def getFileInStore(r: Request, h: SHA256Hash): Task[Response] = {
    val p: JPath = store.getFileLocation(h)
    store.existsInStore(p).flatMap {
      case false => HNotFound()
      case true  => StaticFile.fromFile(p.toFile, Some(r)).fold(HNotFound())(Task.now)
    }
  }

  object Qhash          extends OptionalQueryParamDecoderMatcher[String]("hash")
  object Qname          extends OptionalQueryParamDecoderMatcher[String]("name")
  object QremoteAddress extends OptionalQueryParamDecoderMatcher[String]("remoteAddress")
  object QtxLo          extends OptionalQueryParamDecoderMatcher[Long]("txLo")
  object QtxHi          extends OptionalQueryParamDecoderMatcher[Long]("txHi")
  object QtimeLo        extends OptionalQueryParamDecoderMatcher[String]("timeLo")
  object QtimeHi        extends OptionalQueryParamDecoderMatcher[String]("timeHi")
  object QsortBy        extends OptionalQueryParamDecoderMatcher[String]("sortBy")
  object QsortOrder     extends OptionalQueryParamDecoderMatcher[String]("sortOrder")

  val root = HttpService {

    case req @ GET -> Root / "objects" / hash =>
      val h: SHA256Hash = SHA256Hash(hash)
      futureToTask(db.findFile(h)).flatMap {
        case None    => HNotFound()
        case Some(f) => f.name match {
          case None       => getFileInStore(req, h)
          case Some(name) => PermanentRedirect(Uri(path = s"/objects/$hash/$name"))
        }
      }

    case req @ GET -> Root / "objects" / hash / filename =>
      val h: SHA256Hash = SHA256Hash(hash)
      futureToTask(db.findFile(h)).flatMap {
        case Some(f) if f.name.getOrElse("") == filename => getFileInStore(req, h)
        case _                                           => HNotFound()
      }

    case req @ DELETE -> Root / "objects" / hash =>
      val h: SHA256Hash = SHA256Hash(hash)
      val i: Option[InetAddress] = req.remote.map(_.getAddress())
      val d: Delete = Delete(h, i)
      val response: Task[DeleteStatus] =
        for {
          ds1 <- futureToTask(db.removeFile(d))
          ds2 <- ds1 match {
            case Deleted      => store.deleteFromStore(h)
            case m @ NotFound => Task.now(m)
          }
        } yield ds2
      Ok(response.map(_.asJson.spaces2))

    case req @ POST -> Root / "objects" =>
      req.decode[Multipart] { mp =>
        val tasks: Seq[Task[Option[Added]]]     = mp.parts.map((p: Part) => handlePart(req, p))
        val response: Task[List[Option[Added]]] = Nondeterminism[Task].gather(tasks)
        Ok(response.map(_.asJson.spaces2))
      }

    case req @ GET -> Root / "objects"
        :? Qhash(hash) +& Qname(name) +& QremoteAddress(remoteAddress)
        +& QtxLo(txLo) +& QtxHi(txHi) +& QtimeLo(timeLo) +& QtimeHi(timeHi)
        +& QsortBy(sortBy) +& QsortOrder(sortOrder) =>
      val query: DBQuery =
        DBQuery(
          hash.map(SHA256Hash(_)),
          name,
          remoteAddress.map(InetAddress.getByName _),
          txLo,
          txHi,
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
          })
      val response: Task[Seq[DBQueryResponse]] = futureToTask(db.runQuery(query))
      Ok(response.map(_.asJson.spaces2))
  }
}
