package hyponome.file

import com.typesafe.config.{Config, ConfigFactory}
import java.nio.file.Path
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import hyponome.core._
import hyponome.test._

class TestStore(p: Path) extends HyponomeFile {
  val storePath: Path = p
}

class HyponomeFileSpec extends WordSpecLike with Matchers with ScalaFutures {

  def withTestStoreInstance(testCode: TestStore => Any): Unit = {
    val t: TestStore = new TestStore(testStorePath)
    val storeFuture: Future[Path] = t.createStore()
    val store: Path = Await.result(storeFuture, 5.seconds)
    try {
      testCode(t); ()
    }
    finally {
      deleteFolder(testStorePath); ()
    }
  }

  "An instance of a class that extends HyponomeFile" must {

    "have a getFilePath method" which {
      "returns the correct file store Path" in withTestStoreInstance { t =>
        val hash = testPDFHash
        val (dir: String, file: String) = hash.value.splitAt(2)
        val expected: Path = testStorePath.resolve(dir).resolve(file).toAbsolutePath
        t.getFilePath(hash) should equal(expected)
      }
    }

    "have a copyToStore method" which {
      "copies a file to the correct file store Path" in withTestStoreInstance { t =>
        t.copyToStore(testPDFHash, testPDF).flatMap { p =>
          getSHA256Hash(p)
        }.futureValue should equal(testPDFHash)
      }
      """returns a Future of a Failure(FileAlreadyExistsException)
      when trying to copy a file to a path that already exists""" in withTestStoreInstance { t =>
        t.copyToStore(testPDFHash, testPDF).flatMap { _ =>
          t.copyToStore(testPDFHash, testPDF)
        }.failed.futureValue shouldBe a [java.nio.file.FileAlreadyExistsException]
      }
    }

    "have a existsInStore method" which {
      """returns a Future with the value of true if the specified path
      exists in the file store""" in withTestStoreInstance { t =>
        t.copyToStore(testPDFHash, testPDF).flatMap { p =>
          t.existsInStore(p)
        }.futureValue should equal(true)
      }
      """returns a Future with the value of false if the specified path
      doesn't exist in the file store""" in withTestStoreInstance { t =>
        val testHash: SHA256Hash = SHA256Hash(
          "482bfece08d11246f41ce3dc43480e1b61659fbe0083b754bee09b44b940ae6c"
        )
        t.existsInStore(t.getFilePath(testHash)).futureValue should equal(false)
      }
    }

    "have a deleteFromStore method" which {
      "deletes a file with the specified hash from the file store" in withTestStoreInstance { t =>
        t.copyToStore(testPDFHash, testPDF).flatMap { _ =>
          t.deleteFromStore(testPDFHash)
        }.flatMap { _ =>
          t.existsInStore(t.getFilePath(testPDFHash))
        }.futureValue should equal(false)
      }
      """returns a Future of value Failed(NoSuchFileException) when
      attempting to delete a file which doesn't exist""" in withTestStoreInstance { t =>
        t.deleteFromStore(testPDFHash).failed.futureValue shouldBe a [java.nio.file.NoSuchFileException]
      }
    }
  }
}
