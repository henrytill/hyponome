package hyponome.file

import com.typesafe.config.{Config, ConfigFactory}
import hyponome.core._
import java.net.InetAddress
import java.nio.file._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class TestStore(p: Path) extends HyponomeFile {

  val storePath: Path = p
}

class HyponomeFileSpec extends WordSpecLike with Matchers with ScalaFutures {

  val fs: FileSystem = FileSystems.getDefault()

  /**
    * val hyponomeConfigFile: java.io.File = new java.io.File("hyponome.conf")
    *
    * val config: Config = ConfigFactory.parseFile(hyponomeConfigFile)
    *
    * val configStorePath: Path = fs.getPath(config.getString("file-store.path"))
    */

  val tempStorePath: Path = fs.getPath("/tmp/hyponome/store")

  val testPDF: Path = {
    val s: String = getClass.getResource("/test.pdf").getPath
    fs.getPath(s)
  }

  val testPDFHash = SHA256Hash(
    "eba205fb9114750b2ce83db62f9c2a15dd068bcba31a2de32d8df7f7c8d85441"
  )

  /**
    * val add = Addition(
    *   testPDFHash,
    *   testPDF.toFile.getName,
    *   "application/pdf",
    *   testPDF.toFile.length,
    *   Some(InetAddress.getByName("192.168.1.253"))
    * )
    */

  def withTestStoreInstance(testCode: TestStore => Any): Unit = {
    val t: TestStore = new TestStore(tempStorePath)
    val delete: Try[Path] = t.deleteStore()
    val store: Try[Path] = delete.flatMap { _ =>
      t.createStore()
    }
    store match {
      case Success(p: Path) =>
        // println(s"New test store created at $p")
        testCode(t)
      case Failure(e) =>
        // println(s"New test store couldn't be created: $e")
        fail()
    }
  }

  "An instance of a class that extends HyponomeFile" must {

    "have a getSHA256Hash method" which {
      "returns the correct SHA256 hash for a given file" in withTestStoreInstance { t =>
        t.getSHA256Hash(testPDF) should equal(testPDFHash)
      }
    }

    "have a getFilePath method" which {
      "returns the correct Path" in withTestStoreInstance { t =>
        val hash = t.getSHA256Hash(testPDF)
        val (dir, file) = hash.value.splitAt(2)
        val expected: Path = tempStorePath.resolve(dir).resolve(file).toAbsolutePath
        t.getFilePath(hash) should equal(expected)
      }
    }

    "have a copyToStore method" which {
      "copies a file to the correct Path" in withTestStoreInstance { t =>
        val sourceHash = t.getSHA256Hash(testPDF)
        val destinationPath: Path = t.copyToStore(sourceHash, testPDF).futureValue
        val destinationHash: SHA256Hash = t.getSHA256Hash(destinationPath)
        destinationHash should equal(sourceHash)
      }
    }
  }
}
