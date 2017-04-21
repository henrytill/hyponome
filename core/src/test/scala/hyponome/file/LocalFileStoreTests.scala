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

package hyponome.file

import java.nio.file.{Files, Path}

import fs2.Strategy
import fs2.interop.cats._
import hyponome._
import hyponome.test._
import org.junit.{Assert, Test}

import scala.concurrent.ExecutionContext

class LocalFileStoreTests {

  import hyponome.file.FileStore._

  implicit val E: ExecutionContext = ExecutionContext.Implicits.global
  implicit val S: Strategy         = Strategy.fromFixedDaemonPool(8, threadName = "worker")

  def addFile(file: Path)(implicit fileStore: FileStore[LocalStore.T, Path]): LocalStore.T[(AddStatus, Option[Path])] =
    for {
      ctx  <- LocalStore.ask
      hash <- LocalStore.fromTask(FileHash.fromPath(file))
      _    <- fileStore.init(ctx.storePath)
      as   <- fileStore.addFile(ctx.storePath, hash, file)
      path <- fileStore.findFile(ctx.storePath, hash)
    } yield (as, path)

  def addAndThenRemoveFile(file: Path)(implicit fileStore: FileStore[LocalStore.T, Path]): LocalStore.T[(RemoveStatus, Option[Path])] =
    for {
      ctx  <- LocalStore.ask
      hash <- LocalStore.fromTask(FileHash.fromPath(file))
      _    <- fileStore.init(ctx.storePath)
      _    <- fileStore.addFile(ctx.storePath, hash, file)
      path <- fileStore.findFile(ctx.storePath, hash)
      rs   <- fileStore.removeFile(ctx.storePath, hash)
    } yield (rs, path)

  def addAndThenAddFile(file: Path)(implicit fileStore: FileStore[LocalStore.T, Path]): LocalStore.T[(AddStatus, Option[Path])] =
    for {
      ctx  <- LocalStore.ask
      hash <- LocalStore.fromTask(FileHash.fromPath(file))
      _    <- fileStore.init(ctx.storePath)
      _    <- fileStore.addFile(ctx.storePath, hash, file)
      path <- fileStore.findFile(ctx.storePath, hash)
      as   <- fileStore.addFile(ctx.storePath, hash, file)
    } yield (as, path)

  def removeNonExistentFile(hash: FileHash)(implicit fileStore: FileStore[LocalStore.T, Path]): LocalStore.T[RemoveStatus] =
    for {
      ctx <- LocalStore.ask
      _   <- fileStore.init(ctx.storePath)
      rs  <- fileStore.removeFile(ctx.storePath, hash)
    } yield rs

  def doubleCreate(implicit fileStore: FileStore[LocalStore.T, Path]): LocalStore.T[FileStoreStatus] =
    for {
      ctx <- LocalStore.ask
      _   <- fileStore.init(ctx.storePath)
      ss  <- fileStore.init(ctx.storePath)
    } yield ss

  @Test
  def runAddFile(): Unit = {
    val (status, Some(path)) = freshTestContext().flatMap((ctx: LocalStoreContext) => addFile(testPDF).run(ctx)).unsafeRun
    val copiedHash: FileHash = FileHash.fromPath(path).unsafeRun
    Assert.assertEquals(Added(testPDFHash), status)
    Assert.assertArrayEquals(testPDFHash.getBytes, copiedHash.getBytes)
  }

  @Test
  def runAddAndThenRemoveFile(): Unit = {
    val (status, Some(path)) =
      freshTestContext().flatMap((ctx: LocalStoreContext) => addAndThenRemoveFile(testPDF).run(ctx)).unsafeRun
    val fileExists: Boolean = Files.exists(path)
    Assert.assertEquals(Removed(testPDFHash), status)
    Assert.assertFalse(fileExists)
  }

  @Test
  def runAddAndThenAddFile(): Unit = {
    val (status, Some(path)) =
      freshTestContext().flatMap((ctx: LocalStoreContext) => addAndThenAddFile(testPDF).run(ctx)).unsafeRun
    val fileExists: Boolean = Files.exists(path)
    Assert.assertEquals(Exists(testPDFHash), status)
    Assert.assertTrue(fileExists)
  }

  @Test
  def runRemoveNonExistentFile(): Unit = {
    val status: RemoveStatus =
      freshTestContext().flatMap((ctx: LocalStoreContext) => removeNonExistentFile(testPDFHash).run(ctx)).unsafeRun
    Assert.assertEquals(NotFound(testPDFHash), status)
  }

  @Test
  def runDoubleCreate(): Unit = {
    val status: FileStoreStatus = freshTestContext().flatMap((ctx: LocalStoreContext) => doubleCreate.run(ctx)).unsafeRun
    Assert.assertEquals(FileStoreExists, status)
  }
}
