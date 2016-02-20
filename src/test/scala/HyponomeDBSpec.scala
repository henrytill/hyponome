package hyponome

import java.util.UUID.randomUUID
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
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
    val db = Database.forURL("jdbc:h2:mem:" + dbName, driver="org.h2.Driver", keepAliveConnection = true)
    val t: TestDB = new TestDB(db)
    try {
      testCode(t)
    }
    finally t.close()
  }

  val add = Addition(
    SHA256Hash("01814411d889d10d474fff484e74c0f90ff5259e241de28851c2561b4ceb28a7"),
    "ShouldMLbeOO.pdf",
    "application/pdf",
    164943,
    "192.168.1.253"
  )

  val remove = Removal(
    SHA256Hash("01814411d889d10d474fff484e74c0f90ff5259e241de28851c2561b4ceb28a7"),
    "192.168.1.253"
  )

  val expected = File(
    SHA256Hash("01814411d889d10d474fff484e74c0f90ff5259e241de28851c2561b4ceb28a7"),
    "ShouldMLbeOO.pdf",
    "application/pdf",
    164943
  )

  "An instance of a class that extends HyponomeDB" must {

    "have a createDB method" which {
      """returns a Future value of Success(()) when attempting to
      create a db if one doesn't already exist""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
      }
      """returns a Future value of Failure(JdbcSQLException) when
      attempting to create a db if one already exits""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.createDB.failed.futureValue shouldBe a [org.h2.jdbc.JdbcSQLException]
      }
    }

    "have an addFile method" which {
      "returns a Future value of Success(()) when adding a file" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
      }
      """returns a Future value of Failure(JdbcSQLException) when
      adding a file that has already been added""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.addFile(add).failed.futureValue shouldBe a [org.h2.jdbc.JdbcSQLException]
      }
    }

    "have a removeFile method" which {
      "returns a Future value of Success(()) when removing a file" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.removeFile(remove).futureValue should equal(())
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
      argument of that file's hash""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.findFile(add.hash).futureValue should equal(expected)
      }
      """returns a Future value of Failure(IllegalArgumentException)
      when called with an argument of the hash of a file that has been
      removed""" in withTestDBInstance { t =>
        t.createDB.futureValue should equal(())
        t.addFile(add).futureValue should equal(())
        t.removeFile(remove).futureValue should equal(())
        t.findFile(add.hash).failed.futureValue shouldBe a [IllegalArgumentException]
      }
    }
  }
}
