/*
 * Copyright 2016-2017 Henry Till
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

import fs2.{Strategy, Task}
import fs2.interop.cats._
import hyponome._
import hyponome.test._
import org.junit.{Assert, Test}
import scala.concurrent.ExecutionContext
import slick.driver.SQLiteDriver.backend.DatabaseDef

@SuppressWarnings(Array("org.wartremover.warts.Nothing"))
class SQLFileDBTest {

  import hyponome.db.FileDB._

  implicit val E: ExecutionContext = ExecutionContext.Implicits.global
  implicit val S: Strategy         = Strategy.fromFixedDaemonPool(8, threadName = "worker")

  def safeRun[A, B, C](ctx: LocalStoreContext, thing: => LocalStore.T[C])(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): Task[C] =
    for {
      result <- thing.run(ctx).handleWith { case ex: Throwable => fileDB.close(ctx.dbDef); Task.fail(ex) }
      _      <- Task(fileDB.close(ctx.dbDef))
    } yield result

  def singleAddTest(testData: TestData)(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[AddStatus] =
    for {
      ctx <- LocalStore.ask
      _   <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
      afs <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
    } yield afs

  def addThenRemove(testData: TestData)(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[RemoveStatus] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
      afs <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
      rfs <- fileDB.removeFile(ctx.dbDef, testData.hash, testData.user, testMessageRemove)
    } yield rfs

  def addThenAdd(testData: TestData)(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[AddStatus] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
      as1 <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
      as2 <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
    } yield as2

  def addThenRemoveThenAdd(testData: TestData)(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[AddStatus] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
      as1 <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
      rfs <- fileDB.removeFile(ctx.dbDef, testData.hash, testData.user, testMessageRemove)
      as2 <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
    } yield as2

  def addThenRemoveThenRemove(testData: TestData)(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[RemoveStatus] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
      afs <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
      rs1 <- fileDB.removeFile(ctx.dbDef, testData.hash, testData.user, testMessageRemove)
      rs2 <- fileDB.removeFile(ctx.dbDef, testData.hash, testData.user, testMessageRemove)
    } yield rs2

  def addThenFind(testData: TestData)(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[Option[File]] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
      afs <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
      fff <- fileDB.findFile(ctx.dbDef, testData.hash)
    } yield fff

  def addThenRemoveThenFind(testData: TestData)(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[Option[File]] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
      afs <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
      rfs <- fileDB.removeFile(ctx.dbDef, testData.hash, testData.user, testMessageRemove)
      fff <- fileDB.findFile(ctx.dbDef, testData.hash)
    } yield fff

  def doubleInit(implicit fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[DBStatus] =
    for {
      ctx <- LocalStore.ask
      _   <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
      ds  <- fileDB.init(ctx.dbDef, ctx.dbSchemaVersion)
    } yield ds

  @Test
  def runSingleAddTest(): Unit = {
    val actual = freshTestContext().flatMap((ctx: LocalStoreContext) => safeRun(ctx, singleAddTest(testData))).unsafeRun
    Assert.assertEquals(Added(testData.hash), actual)
  }

  @Test
  def runAddThenRemove(): Unit = {
    val actual = freshTestContext().flatMap((ctx: LocalStoreContext) => safeRun(ctx, addThenRemove(testData))).unsafeRun
    Assert.assertEquals(Removed(testData.hash), actual)
  }

  @Test
  def runAddThenAdd(): Unit = {
    val actual = freshTestContext().flatMap((ctx: LocalStoreContext) => safeRun(ctx, addThenAdd(testData))).unsafeRun
    Assert.assertEquals(Exists(testData.hash), actual)
  }

  @Test
  def runAddThenRemoveThenAdd(): Unit = {
    val actual = freshTestContext().flatMap((ctx: LocalStoreContext) => safeRun(ctx, addThenRemoveThenAdd(testData))).unsafeRun
    Assert.assertEquals(Added(testData.hash), actual)
  }

  @Test
  def runAddThenRemoveThenRemove(): Unit = {
    val actual = freshTestContext().flatMap((ctx: LocalStoreContext) => safeRun(ctx, addThenRemoveThenRemove(testData))).unsafeRun
    Assert.assertEquals(NotFound(testData.hash), actual)
  }

  @Test
  def runAddThenFind(): Unit = {
    val actual = freshTestContext().flatMap((ctx: LocalStoreContext) => safeRun(ctx, addThenFind(testData))).unsafeRun
    Assert.assertEquals(Some(testFile), actual)
  }

  @Test
  def runAddThenRemoveThenFind(): Unit = {
    val actual = freshTestContext().flatMap((ctx: LocalStoreContext) => safeRun(ctx, addThenRemoveThenFind(testData))).unsafeRun
    Assert.assertEquals(None, actual)
  }

  @Test
  def runDoubleInit(): Unit = {
    val actual = freshTestContext().flatMap((ctx: LocalStoreContext) => safeRun(ctx, doubleInit)).unsafeRun
    Assert.assertEquals(DBExists, actual)
  }
}
