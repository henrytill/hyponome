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

package hyponome.db

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.time.{Millis, Span}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.core._
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

  def withPersistentDBConfig(testCode: (Function0[DatabaseDef], Path) => Any): Unit = {
    val p  = fs.getPath("/tmp/hyponome/" + makeDbName())
    val db = makePersistentDBConfig(p)
    testCode(db, p); ()
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
      "returns a Future value of Success(Created) when adding a file" in withTestDBInstance { t =>
        t.create().flatMap { _ => t.addFile(add) }.futureValue should equal(Created)
      }
      """returns a Future value of Success(Created) when adding a file that
      has already been removed""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.flatMap { _ =>
          t.addFile(add)
        }.futureValue should equal (Created)
      }
      """returns a Future value of Success(Exists) when
      adding a file that has already been added""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.addFile(add)
        }.futureValue should equal (Exists)
      }
    }

    "have a removeFile method" which {
      "returns a Future value of Success(Removed) when removing a file" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.futureValue should equal (Deleted)
      }
      """returns a Future value of Success(NotFound)
      when removing a file that has never beend added""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.removeFile(remove)
        }.futureValue should equal (NotFound)
      }
      """returns a Future value of Success(NotFound)
      when removing a file that has already been removed""" in withTestDBInstance { t =>
        t.create().flatMap { _ =>
          t.addFile(add)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.flatMap { _ =>
          t.removeFile(remove)
        }.futureValue should equal (NotFound)
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

    "have a syncCounter method" which {
      "returns a Future value of Unit and syncs the counter (1)" in withPersistentDBConfig { (c, p) =>
        // initial db
        val q: TestDB = new TestDB(c(), makeCounter())
        val addRemoveFuture01 =
          q.create()
            .flatMap { _ => q.addFile(add) }
            .flatMap { _ => q.removeFile(remove) }
            .flatMap { _ => q.addFile(add) }
            .flatMap { _ => q.removeFile(remove) }
        val tmp01: DeleteStatus = Await.result(addRemoveFuture01, 5.seconds)
        q.close()
        // re-open initial db
        val r: TestDB = new TestDB(c(), makeCounter())
        val syncFuture01: Future[Unit] = r.syncCounter()
        val tmp02: Unit = Await.result(syncFuture01, 5.seconds)
        syncFuture01.futureValue should equal (())
        r.counter.get should equal (4)
        deleteFolder(p.getParent)
      }
      "returns a Future value of Unit and syncs the counter (2)" in withPersistentDBConfig { (c, p) =>
        // initial db
        val q: TestDB = new TestDB(c(), makeCounter())
        val addRemoveFuture01 =
          q.create()
            .flatMap { _ => q.addFile(add) }
            .flatMap { _ => q.removeFile(remove) }
            .flatMap { _ => q.addFile(add) }
            .flatMap { _ => q.removeFile(remove) }
        val tmp01: DeleteStatus = Await.result(addRemoveFuture01, 5.seconds)
        q.close()
        // re-open initial db
        val r: TestDB = new TestDB(c(), makeCounter())
        val syncFuture01: Future[Unit] = r.syncCounter()
        val addRemoveFuture02 = syncFuture01.flatMap { _ =>
          r.addFile(add)
            .flatMap { _ => r.removeFile(remove) }
            .flatMap { _ => r.addFile(add) }
        }
        val tmp02: PostStatus = Await.result(addRemoveFuture02, 5.seconds)
        r.close()
        // re-re-open initial db
        val s: TestDB = new TestDB(c(), makeCounter())
        val syncFuture02: Future[Unit] = s.syncCounter()
        val tmp03: Unit = Await.result(syncFuture02, 5.seconds)
        syncFuture02.futureValue should equal (())
        s.counter.get should equal (7)
        deleteFolder(p.getParent)
      }
    }
  }
}
