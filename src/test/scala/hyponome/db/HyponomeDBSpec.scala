package hyponome.db

import java.util.concurrent.atomic.AtomicLong
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.time.{Millis, Span}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.test._

class TestDB(dbDef: DatabaseDef, count: AtomicLong) extends HyponomeDB {

  val files: TableQuery[Files] = TableQuery[Files]

  val events: TableQuery[Events] = TableQuery[Events]

  val db: DatabaseDef = dbDef

  val counter: AtomicLong = count
}

class HyponomeDBSpec extends WordSpecLike with Matchers with ScalaFutures {

  def withTestDBInstance(testCode: TestDB => Any): Unit = {
    val t: TestDB = new TestDB(makeTestDB(), makeCounter())
    try {
      testCode(t); ()
    }
    finally t.close()
  }

  implicit val patience: PatienceConfig = PatienceConfig(
    Span(300, Millis),
    Span(15, Millis)
  )

  "An instance of a class that extends HyponomeDB" must {

    "have a create method" which {
      """returns a Future value of Success(()) when attempting to
      create a db if one doesn't already exist""" in withTestDBInstance { t =>
        t.create().futureValue should equal(())
      }
      """returns a Future value of Failure(JdbcSQLException) when
      attempting to create a db if one already exists""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.create()
        }.failed.futureValue shouldBe a [org.h2.jdbc.JdbcSQLException]
      }
    }

    "have an exists method" which {
      """returns a Future value of true if the db specified by `db`
      exists""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.exists
        }.futureValue should equal(true)
      }
      """returns a Future value of false if the db specified by `db`
      doesn't exist""" in withTestDBInstance { t =>
        t.exists.futureValue should equal(false)
      }
    }

    "have an addFile method" which {
      "returns a Future value of Success(()) when adding a file" in withTestDBInstance { t =>
        t.create().flatMap { _ => t.addFile(add) }.futureValue should equal(())
      }
      """returns a Future value of Success(()) when adding a file that
      has already been removed""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.flatMap { _ =>
          t.addFile(add)
        }.futureValue should equal (())
      }
      """returns a Future value of Failure(UnsupportedOperationException) when
      adding a file that has already been added""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.addFile(add)
        }.failed.futureValue shouldBe a [UnsupportedOperationException]
      }
    }

    "have a removeFile method" which {
      "returns a Future value of Success(()) when removing a file" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.futureValue should equal (())
      }
      """returns a Future value of Failure(UnsupportedOperationException)
      when removing a file that has never beend added""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.removeFile(remove)
        }.failed.futureValue shouldBe a [UnsupportedOperationException]
      }
      """returns a Future value of Failure(UnsupportedOperationException)
      when removing a file that has already been removed""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.failed.futureValue shouldBe a [UnsupportedOperationException]
      }
    }

    "have a findFile method" which {
      """returns a Future value of a given File when called with an
      argument of a file's hash that has never been added""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.findFile(add.hash)
        }.futureValue should equal (None)
      }
      """returns a Future value of a given File when called with an
      argument of that file's hash""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.findFile(add.hash)
        }.futureValue should equal (Some(expected))
      }
      """returns a Future value of Failure(IllegalArgumentException)
      when called with an argument of the hash of a file that has been
      removed""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.flatMap { _ =>
          t.findFile(add.hash)
        }.futureValue should equal (None)
      }
    }

    "have a maxTx method" which {
      """returns a Future value of a None when called with an empty
      Events table""" in withTestDBInstance { t =>
        t.create().flatMap { _ => t.maxTx }.futureValue should equal(None)
      }
      """returns a Future value which corresponds to the number of
      items in the Events table""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.flatMap { _ =>
          t.maxTx
        }.futureValue should equal (Some(2))
      }
    }
  }
}
