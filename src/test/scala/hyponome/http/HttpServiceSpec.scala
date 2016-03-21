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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.scaladsl.StreamConverters
import com.typesafe.config.{Config, ConfigFactory}
import java.net.InetAddress
import java.nio.file._
import java.util.UUID.randomUUID
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.prop.PropertyChecks
import org.scalatest.time.{Millis, Span}
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import hyponome.core._
import hyponome.test._

class HttpServiceSpec extends WordSpecLike
    with Matchers
    with PatienceConfiguration
    with PropertyChecks
    with ScalaFutures {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val http = Http(system)

  def createEntity(file: java.io.File): Future[RequestEntity] = {
    val f = FileIO.fromFile(file)
    val e = HttpEntity(MediaTypes.`application/octet-stream`, file.length, f)
    val s = Source.single(Multipart.FormData.BodyPart("file", e, Map("filename" -> file.getName)))
    Marshal(Multipart.FormData(s)).to[RequestEntity]
  }

  def postFile(p: Path, u: String): Future[HttpResponse] =
    createEntity(p.toFile).flatMap { e =>
      http.singleRequest(HttpRequest(method = HttpMethods.POST, uri = u, entity = e))
    }

  def roundTripper(p: Path, u: String): Future[SHA256Hash] = {
    def convertToRequest(location: Option[String]): Future[HttpResponse] =
      location match {
        case Some(loc) =>
          val req = HttpRequest(method = HttpMethods.GET, uri = loc)
          http.singleRequest(req)
        case None =>
          Future.failed(new Exception("Header did not contain a Location"))
      }
    postFile(p, u)
      .map(_.headers)
      .map(_.find(_.name == "Location"))
      .map(_.map(_.value))
      .flatMap(convertToRequest)
      .map(_.entity)
      .map(_.dataBytes)
      .map(_.runWith(StreamConverters.asInputStream()))
      .map(sha256Hex)
      .map(SHA256Hash(_))
  }

  override implicit def patienceConfig = PatienceConfig(
    timeout = Span(2000, Millis),
    interval = Span(15, Millis)
  )

  def withHttpService(testCode: HyponomeConfig => Any): Unit = {
    val testConfig: HyponomeConfig = HyponomeConfig(makeTestDB, testStorePath, hostname, port, "file")
    val service: HttpService = HttpService(testConfig).start()
    try {
      testCode(testConfig); ()
    }
    finally {
      val stopped: HttpService = service.stop()
      deleteFolder(testStorePath); ()
    }
  }

  "An instance of HttpService" must {

    "do this" in withHttpService { cfg =>
      val u = s"http://${cfg.hostname}:${cfg.port}/objects"
      roundTripper(testPDF, u).futureValue shouldEqual testPDFHash
    }

    "do that" in withHttpService { cfg =>
      val testFiles: Path = fs.getPath("/tmp/hyponome/test")
      val pathCreated = Files.createDirectories(testFiles)
      forAll { (ba: Array[Byte]) =>
        val u = s"http://${cfg.hostname}:${cfg.port}/objects"
        lazy val testFileHash: String = sha256Hex(ba)
        lazy val testFilePath: Path   = testFiles.resolve(testFileHash)
        Files.write(testFilePath, ba)
        val printPath: String = "%.40s".format(testFilePath)
        println(s"Round-tripping $printPath...")
        roundTripper(testFilePath, u).futureValue.value shouldEqual testFileHash
      }
      deleteFolder(testFiles)
    }
  }
}
