package hyponome.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.typesafe.config.{Config, ConfigFactory}
import hyponome.core._
import hyponome.file._
import java.net.InetAddress
import java.nio.file._
import java.util.UUID.randomUUID
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Millis, Span}
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef

class HttpServiceSpec extends WordSpecLike
    with Matchers
    with PatienceConfiguration
    with ScalaFutures {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val http = Http(system)

  val fs: FileSystem = FileSystems.getDefault()

  val testPDF: Path = {
    val s: String = getClass.getResource("/test.pdf").getPath
    fs.getPath(s)
  }

  val testPDFHash = SHA256Hash(
    "eba205fb9114750b2ce83db62f9c2a15dd068bcba31a2de32d8df7f7c8d85441"
  )

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

  override implicit def patienceConfig = PatienceConfig(
    timeout = Span(2000, Millis),
    interval = Span(500, Millis)
  )

  def withHttpService(testCode: HyponomeConfig => Any): Unit = {
    val testStorePath: Path = fs.getPath("/tmp/hyponome/store")
    val dbName = randomUUID.toString
    val testDb: DatabaseDef = Database.forURL(
      url = s"jdbc:h2:mem:$dbName;CIPHER=AES",
      user = "hyponome",
      password = "hyponome hyponome", // password = "filepwd userpwd"
      driver = "org.h2.Driver",
      keepAliveConnection = true
    )
    val hostname: String = InetAddress.getLocalHost().getHostName()
    val testConfig = HyponomeConfig(testDb, testStorePath, hostname, 3000, "file")
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
      val f = postFile(testPDF, u)
      f onComplete {
        case x => println(x)
      }
      f.futureValue shouldBe a [HttpResponse]
    }

    "do that" in withHttpService { cfg =>
      1 should equal(1)
    }
  }
}
