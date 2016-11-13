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
import java.nio.file.{Path => JPath, Files => JFiles}
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.http4s.EntityEncoder._
import org.http4s.MediaType._
import org.http4s.Uri._
import org.http4s._
import org.http4s.argonaut._
import org.http4s.client.blaze._
import org.http4s.headers._
import org.http4s.multipart._
import org.http4s.util._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.nonEmptyContainerOf
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, WordSpecLike}
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.concurrent.Task
import scalaz.stream.io.fileChunkW
import hyponome._
import hyponome.db.SQLFileDB
import hyponome.file.LocalFileStore
import hyponome.http.config._
import hyponome.http.JsonProtocol._
import hyponome.test._
import hyponome.util._

class ServiceSpec extends WordSpecLike with Matchers with PropertyChecks {

  val logger: Logger = LoggerFactory.getLogger(classOf[ServiceSpec])

  def createTmpDir(): JPath = JFiles.createTempDirectory("hyponome-test-downloads-")
  val tmpDir: JPath = createTmpDir()

  val clientConfig: BlazeClientConfig =
    BlazeClientConfig.defaultConfig.copy(sslContext = Some(clientContext))

  val client = PooledHttp1Client(config = clientConfig)

  val url = Uri(scheme = Some(CaseInsensitiveString("https")),
                authority = Some(Authority(host = RegName(testHostname), port = Some(testPort))),
                path = "/objects")

  def createRequest(ps: Seq[JPath]): Request = {
    val parts: Vector[Part] = ps.map { (p: JPath) =>
      Part.fileData("file", p.toFile, `Content-Type`(`application/octet-stream`))
    }.toVector
    val multipart = Multipart(parts)
    val entity    = EntityEncoder[Multipart].toEntity(multipart)
    val body      = entity.unsafePerformSync.body
    Request(method = Method.POST,
            uri = url,
            body = body,
            headers = multipart.headers)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  def handleGet(r: Response): Task[SHA256Hash] = {
    val tempFilePath: JPath = tmpDir.resolve(s"${randomUUID()}")
    r.body.to(fileChunkW(tempFilePath.toFile.toString)).run.flatMap { (_: Unit) =>
      getSHA256Hash(tempFilePath)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  def fetchAndHash(ss: Vector[String]): Task[List[SHA256Hash]] =
    Task.gatherUnordered(ss.map((s: String) => client.get(s)(handleGet)))

  @SuppressWarnings(Array("org.wartremover.warts.Nothing", "org.wartremover.warts.NoNeedForMonad"))
  def upAndDown(r: Request): Task[List[SHA256Hash]] =
    client
      .expect(r)(EntityDecoder[Json])
      .map(_.as[Vector[AddResponse]].toOption)
      .map(_.getOrElse(Vector.empty))
      .map(_.map(_.file.toString))
      .flatMap(fetchAndHash)

  def roundTrip(ps: Seq[JPath]): Task[List[SHA256Hash]] =
    upAndDown(createRequest(ps))

  def withService(testCode: Task[Unit]): Task[Unit] = {
    val testConfig: ServiceConfig = ServiceConfig(
      makeTestDB, testStorePath, testHostname, testPort, "file")
    for {
      db  <- Task.now(new SQLFileDB(testConfig.db))
      _   <- futureToTask(db.init())
      st  <- Task.now(new LocalFileStore(testConfig.store))
      _   <- st.init()
      ls  <- Task.now(new LocalStore(db, st))
      svc <- Task.now(new Service(testConfig, ls))
      srv <- testServer(testConfig, svc.root)
      _   <- testCode
      _   <- srv.shutdown
    } yield ()
  }

  val genNonEmptyByteArray = nonEmptyContainerOf[Array, Byte](arbitrary[Byte])

  "An instance of Service" must {

    "successfully round-trip a test file" in withService {
      roundTrip(List(testPDF)).flatMap { (xs: List[SHA256Hash]) =>
        Task.now(xs shouldEqual List(testPDFHash))
      }
    }.unsafePerformSync

    "successfully round-trip 100 randomly-generated test files" in withService {
      Task.delay {
        forAll(genNonEmptyByteArray) { (ba: Array[Byte]) =>
          (for {
            t <- Task.now(JFiles.createTempDirectory("hyponome-test-uploads-"))
            h <- Task.now(sha256Hex(ba))
            p <- Task.now(t.resolve(h))
            _ <- Task.now(JFiles.write(p, ba))
            _ <- Task.now(logger.info(s"Round-tripping $p"))
            r <- roundTrip(List(p))
          } yield r shouldEqual List(SHA256Hash(h))).unsafePerformSync
        }
      }
    }.unsafePerformSync
  }
}
