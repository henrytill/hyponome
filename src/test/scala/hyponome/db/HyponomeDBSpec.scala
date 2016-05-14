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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.time.{Millis, Span}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await}
import scala.concurrent.duration._
import scalaz.concurrent.Task
import slick.driver.H2Driver.backend.DatabaseDef
import hyponome.test._
import hyponome.event._
import hyponome.util._

class HyponomeDBSpec extends WordSpecLike with Matchers with ScalaFutures {

  def withDBInstance(testCode: HyponomeDB => Any): Unit = {
    val t: HyponomeDB = (for {
      db <- Task.now(new HyponomeDB(makeTestDB))
      _  <- futureToTask(db.init())
    } yield db).unsafePerformSync
    try { testCode(t); () }
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

    "have an add method" which {
      "returns a Future value of Success(Added) when adding a file" in withDBInstance { t =>
        t.add(add).futureValue should equal(Added)
      }
      """|returns a Future value of Success(Added) when adding a file that has
         |already been removed""".stripMargin in withDBInstance { t =>
        t.add(add).flatMap { _ =>
          t.remove(remove)
        }.flatMap { _ =>
          t.add(add)
        }.futureValue should equal (Added)
      }
      """|returns a Future value of Success(Exists) when adding a file that has
         |already been added""".stripMargin in withDBInstance { t =>
        t.add(add).flatMap { _ =>
          t.add(add)
        }.futureValue should equal (Exists)
      }
    }

    "have a remove method" which {
      "returns a Future value of Success(Removed) when removing a file" in withDBInstance { t =>
        t.add(add).flatMap { _ =>
          t.remove(remove)
        }.futureValue should equal (Removed)
      }
      """|returns a Future value of Success(NotFound) when removing a file that has
         |never been added""".stripMargin in withDBInstance { t =>
        t.remove(remove).futureValue should equal (NotFound)
      }
      """|returns a Future value of Success(NotFound) when removing a file that has
         |already been removed""".stripMargin in withDBInstance { t =>
        t.add(add).flatMap { _ =>
          t.remove(remove)
        }.flatMap { _ =>
          t.remove(remove)
        }.futureValue should equal (NotFound)
      }
    }

    "have a find method" which {
      """|returns a Future value of a given File when called with an argument of a
         |file's hash that has never been added""".stripMargin in withDBInstance { t =>
        t.find(add.hash).futureValue should equal (None)
      }
      """|returns a Future value of a given File when called with an argument of that
         |file's hash""".stripMargin in withDBInstance { t =>
        t.add(add).flatMap { _ =>
          t.find(add.hash)
        }.futureValue should equal (Some(expected))
      }
      """|returns a Future value of Failure(IllegalArgumentException) when called with
         |an argument of the hash of a file that has been removed""".stripMargin in withDBInstance { t =>
        t.add(add).flatMap { _ =>
          t.remove(remove)
        }.flatMap { _ =>
          t.find(add.hash)
        }.futureValue should equal (None)
      }
    }

    "have a syncCounter method" which {
      "returns a Future value of Unit and syncs the counter (1)" in withPersistentDBConfig { (c, p) =>
        // initial db
        val q: HyponomeDB = (for {
          db <- Task.now(new HyponomeDB(c))
          _  <- futureToTask(db.init())
        } yield db).unsafePerformSync
        val addRemoveFuture01 =
          q.add(add)
            .flatMap { _ => q.remove(remove) }
            .flatMap { _ => q.add(add) }
            .flatMap { _ => q.remove(remove) }
        val tmp01: RemoveStatus = Await.result(addRemoveFuture01, 5.seconds)
        q.close()
        // re-open initial db
        val r: HyponomeDB = (for {
          db <- Task.now(new HyponomeDB(c))
          _  <- futureToTask(db.init())
        } yield db).unsafePerformSync
        r.tx.get should equal (4)
        deleteFolder(p.getParent)
      }
      "returns a Future value of Unit and syncs the counter (2)" in withPersistentDBConfig { (c, p) =>
        // initial db
        val q: HyponomeDB = (for {
          db <- Task.now(new HyponomeDB(c))
          _  <- futureToTask(db.init())
        } yield db).unsafePerformSync
        val addRemoveFuture01 =
          q.add(add)
            .flatMap { _ => q.remove(remove) }
            .flatMap { _ => q.add(add) }
            .flatMap { _ => q.remove(remove) }
        val tmp01: RemoveStatus = Await.result(addRemoveFuture01, 5.seconds)
        q.close()
        // re-open initial db
        val r: HyponomeDB = (for {
          db <- Task.now(new HyponomeDB(c))
          _  <- futureToTask(db.init())
        } yield db).unsafePerformSync
        val addRemoveFuture02 =
          r.add(add)
            .flatMap { _ => r.remove(remove) }
            .flatMap { _ => r.add(add) }
        val tmp02: AddStatus = Await.result(addRemoveFuture02, 5.seconds)
        r.close()
        // re-re-open initial db
        val s: HyponomeDB = (for {
          db <- Task.now(new HyponomeDB(c))
          _  <- futureToTask(db.init())
        } yield db).unsafePerformSync
        s.tx.get should equal (7)
        deleteFolder(p.getParent)
      }
    }
  }
}
