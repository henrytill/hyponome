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

import hyponome._
import hyponome.test._
import org.junit.{Assert, Test}
import scala.concurrent.ExecutionContext
import scalaz.concurrent.Task
import slick.driver.SQLiteDriver.backend.DatabaseDef

@SuppressWarnings(Array("org.wartremover.warts.Nothing"))
class SQLFileDBTest {

  import scala.concurrent.ExecutionContext.Implicits.global
  import hyponome.db.FileDB._

  def safeRun[A, B, C](ctx: LocalStoreContext, thing: => LocalStore.T[C])(implicit ec: ExecutionContext,
                                                                          fileDB: FileDB[LocalStore.T, DatabaseDef]): Task[C] =
    for {
      result <- thing.run(ctx).handleWith { case ex: Throwable => fileDB.close(ctx.dbDef); Task.fail(ex) }
      _      <- Task(fileDB.close(ctx.dbDef))
    } yield result

  def singleAddTest(testData: TestData)(implicit ec: ExecutionContext,
                                        fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[AddStatus] =
    for {
      ctx <- LocalStore.ask
      _   <- fileDB.init(ctx.dbDef)
      afs <- fileDB.addFile(ctx.dbDef,
                            testData.hash,
                            testData.name,
                            testData.contentType,
                            testData.length,
                            testData.metadata,
                            testData.user,
                            testData.message)
    } yield afs

  def addThenRemove(testData: TestData)(implicit ec: ExecutionContext,
                                        fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[RemoveStatus] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef)
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

  def addThenAdd(testData: TestData)(implicit ec: ExecutionContext, fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[AddStatus] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef)
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

  def addThenRemoveThenAdd(testData: TestData)(implicit ec: ExecutionContext,
                                               fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[AddStatus] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef)
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

  def addThenRemoveThenRemove(testData: TestData)(implicit ec: ExecutionContext,
                                                  fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[RemoveStatus] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef)
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

  def addThenFind(testData: TestData)(implicit ec: ExecutionContext,
                                      fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[Option[File]] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef)
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

  def addThenRemoveThenFind(testData: TestData)(implicit ec: ExecutionContext,
                                                fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[Option[File]] =
    for {
      ctx <- LocalStore.ask
      iss <- fileDB.init(ctx.dbDef)
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

  def doubleInit(implicit ec: ExecutionContext, fileDB: FileDB[LocalStore.T, DatabaseDef]): LocalStore.T[DBStatus] =
    for {
      ctx <- LocalStore.ask
      _   <- fileDB.init(ctx.dbDef)
      ds  <- fileDB.init(ctx.dbDef)
    } yield ds

  @Test
  def runSingleAddTest(): Unit = {
    val actual = safeRun(freshTestContext(), singleAddTest(testData)).unsafePerformSync
    Assert.assertEquals(Added, actual)
  }

  @Test
  def runAddThenRemove(): Unit = {
    val actual = safeRun(freshTestContext(), addThenRemove(testData)).unsafePerformSync
    Assert.assertEquals(Removed, actual)
  }

  @Test
  def runAddThenAdd(): Unit = {
    val actual = safeRun(freshTestContext(), addThenAdd(testData)).unsafePerformSync
    Assert.assertEquals(Exists, actual)
  }

  @Test
  def runAddThenRemoveThenAdd(): Unit = {
    val actual = safeRun(freshTestContext(), addThenRemoveThenAdd(testData)).unsafePerformSync
    Assert.assertEquals(Added, actual)
  }

  @Test
  def runAddThenRemoveThenRemove(): Unit = {
    val actual = safeRun(freshTestContext(), addThenRemoveThenRemove(testData)).unsafePerformSync
    Assert.assertEquals(NotFound, actual)
  }

  @Test
  def runAddThenFind(): Unit = {
    val actual = safeRun(freshTestContext(), addThenFind(testData)).unsafePerformSync
    Assert.assertEquals(Some(testFile), actual)
  }

  @Test
  def runAddThenRemoveThenFind(): Unit = {
    val actual = safeRun(freshTestContext(), addThenRemoveThenFind(testData)).unsafePerformSync
    Assert.assertEquals(None, actual)
  }

  @Test
  def runDoubleInit(): Unit = {
    val actual = safeRun(freshTestContext(), doubleInit).unsafePerformSync
    Assert.assertEquals(DBExists, actual)
  }
}
