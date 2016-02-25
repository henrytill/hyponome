package hyponome.db

import hyponome.core._
import java.net.InetAddress
import java.nio.file._
import java.util.UUID.randomUUID
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.time.{Millis, Span}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef

class TestDB(dbDef: DatabaseDef) extends HyponomeDB {

  val files: TableQuery[Files] = TableQuery[Files]

  val events: TableQuery[Events] = TableQuery[Events]

  val db: DatabaseDef = dbDef
}

class HyponomeDBSpec extends WordSpecLike with Matchers with ScalaFutures {

  def withTestDBInstance(testCode: TestDB => Any): Unit = {
    val dbName = randomUUID.toString
    val db = Database.forURL(
      url = s"jdbc:h2:mem:$dbName;CIPHER=AES",
      user = "hyponome",
      password = "hyponome hyponome", // password = "filepwd userpwd"
      driver = "org.h2.Driver",
      keepAliveConnection = true
    )
    val t: TestDB = new TestDB(db)
    try {
      testCode(t)
      ()
    }
    finally t.close()
  }

  val fs: FileSystem = FileSystems.getDefault()

  val testPDF: Path = {
    val s: String = getClass.getResource("/test.pdf").getPath
    fs.getPath(s)
  }

  val testPDFHash = SHA256Hash(
    "eba205fb9114750b2ce83db62f9c2a15dd068bcba31a2de32d8df7f7c8d85441"
  )

  val ip: Option[InetAddress] = Some(InetAddress.getByName("192.168.1.253"))

  val add = Addition(
    testPDF,
    getSHA256Hash(testPDF),
    testPDF.toFile.getName,
    "application/octet-stream",
    testPDF.toFile.length,
    ip
  )

  val remove = Removal(
    add.hash,
    add.remoteAddress
  )

  val expected = File(
    add.hash,
    add.name,
    add.contentType,
    add.length
  )

  implicit val patience: PatienceConfig = PatienceConfig(
    Span(300, Millis),
    Span(15, Millis)
  )

  "An instance of a class that extends HyponomeDB" must {

    "have a createDB method" which {
      """returns a Future value of Success(()) when attempting to
      create a db if one doesn't already exist""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
      }
      """returns a Future value of Failure(JdbcSQLException) when
      attempting to create a db if one already exists""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.createDB.failed.futureValue shouldBe a [org.h2.jdbc.JdbcSQLException]
      }
    }

    "have an addFile method" which {
      "returns a Future value of Success(()) when adding a file" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
      }
      """returns a Future value of Success(()) when adding a file that
      has already been removed""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.removeFile(remove).futureValue should equal(())
        t.addFile(add).futureValue should equal(())
      }
      """returns a Future value of Failure(UnsupportedOperationException) when
      adding a file that has already been added""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.addFile(add).failed.futureValue shouldBe a [UnsupportedOperationException]
      }
    }

    "have a removeFile method" which {
      "returns a Future value of Success(()) when removing a file" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.removeFile(remove).futureValue should equal(())
      }
      """returns a Future value of Failure(UnsupportedOperationException)
      when removing a file that has never beend added""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.removeFile(remove).failed.futureValue shouldBe a [UnsupportedOperationException]
      }
      """returns a Future value of Failure(UnsupportedOperationException)
      when removing a file that has already been removed""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.removeFile(remove).futureValue should equal(())
        t.removeFile(remove).failed.futureValue shouldBe a [UnsupportedOperationException]
      }
    }

    "have a findFile method" which {
      """returns a Future value of a given File when called with an
      argument of a file's hash that has never been added""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.findFile(add.hash).futureValue should equal(None)
      }
      """returns a Future value of a given File when called with an
      argument of that file's hash""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.findFile(add.hash).futureValue should equal(Some(expected))
      }
      """returns a Future value of Failure(IllegalArgumentException)
      when called with an argument of the hash of a file that has been
      removed""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.removeFile(remove).futureValue should equal(())
        t.findFile(add.hash).futureValue should equal(None)
      }
    }
  }
}
