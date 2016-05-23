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

package hyponome

import java.io.{File => JFile}
import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}
import scalaz.concurrent.Task
import hyponome.db._
import hyponome.file._
import hyponome.db.query._
import hyponome.util._

class LocalStore(
  dbInst: FileDB[Future], fileStoreInst: FileStore[Task, Path])(implicit ec: ExecutionContext)
    extends Store[Task, Future, Path] {

  val db: FileDB[Future] = dbInst

  val fileStore: FileStore[Task, Path] = fileStoreInst

  def info(h: SHA256Hash): Task[Option[File]]             = futureToTask(db.find(h))
  def count: Task[Long]                                   = futureToTask(db.countFiles)
  def query(q: StoreQuery): Task[Seq[StoreQueryResponse]] = futureToTask(db.runQuery(q))

  private def addToFileDB(a: Add): Task[AddStatus] = futureToTask(db.add(a))

  private def addToFileStore(a: Add): PartialFunction[AddStatus, Task[AddResponse]] = {
    case Exists =>
      info(a.hash).flatMap {
        case Some(f) => Task.now(AddResponse(a.mergeWithFile(f), Exists))
        case None    => Task.fail(new RuntimeException)
      }
    case Added =>
      fileStore.add(a).map {
        case Exists => AddResponse(a, Exists)
        case Added  => AddResponse(a, Added)
      }
  }

  private def removeFromFileDB(r: Remove): Task[RemoveStatus] = futureToTask(db.remove(r))

  private def removeFromFileStore(r: Remove): PartialFunction[RemoveStatus, Task[RemoveResponse]] = {
    case Removed =>
      fileStore.remove(r.hash).flatMap {
        case Removed  => Task.now(RemoveResponse(Removed, r.hash))
        case NotFound => Task.now(RemoveResponse(NotFound, r.hash))
      }
    case NotFound => Task.now(RemoveResponse(NotFound, r.hash))
  }

  def add(a: Add): Task[AddResponse] =
    for {
      as1 <- addToFileDB(a)
      as2 <- addToFileStore(a)(as1)
    } yield as2

  def remove(r: Remove): Task[RemoveResponse] =
    for {
      rs1 <- removeFromFileDB(r)
      rs2 <- removeFromFileStore(r)(rs1)
    } yield rs2

  def exists(h: SHA256Hash): Task[Boolean] =
    info(h).flatMap {
      case None => Task.now(false)
      case Some(_) =>
        val p: Path = fileStore.getFileLocation(h)
        fileStore.existsInStore(p)
    }

  def get(h: SHA256Hash): Task[Option[JFile]] =
    exists(h).map {
      case true  => Some(fileStore.getFileLocation(h).toFile)
      case false => None
    }
}
