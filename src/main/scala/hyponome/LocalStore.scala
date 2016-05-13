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
import scala.concurrent.ExecutionContext
import scalaz.concurrent.Task
import hyponome.config._
import hyponome.db._
import hyponome.event._
import hyponome.file._
import hyponome.util._

class LocalStore(dbInst: HyponomeDB, fileStoreInst: FileStore[Path])
                (implicit ec: ExecutionContext)
    extends Store[Path] {

  val db: HyponomeDB = dbInst

  val fileStore: FileStore[Path] = fileStoreInst

  def info(h: SHA256Hash): Task[Option[File]]             = futureToTask(db.findFile(h))
  def count: Task[Long]                                   = futureToTask(db.countFiles)
  def query(q: StoreQuery): Task[Seq[StoreQueryResponse]] = futureToTask(db.runQuery(q))

  private def addToDB(a: Add): Task[AddStatus] = futureToTask(db.addFile(a))

  private def addToFileStore(a: Add): PartialFunction[AddStatus, Task[Added]] = {
    case Exists => info(a.hash).flatMap {
      case Some(f) => Task.now(Added(a.mergeWithFile(f), Exists))
      case None    => Task.fail(new RuntimeException)
    }
    case Created => fileStore.copyToStore(a).map {
      case Exists  => Added(a, Exists)
      case Created => Added(a, Created)
    }
  }

  def put(a: Add): Task[Added] =
    for {
      x <- addToDB(a)
      y <- addToFileStore(a)(x)
    } yield y

  def exists(h: SHA256Hash): Task[Boolean] =
    info(h).flatMap {
      case None    => Task.now(false)
      case Some(_) =>
        val p: Path = fileStore.getFileLocation(h)
        fileStore.existsInStore(p)
    }

  def get(h: SHA256Hash): Task[Option[JFile]] =
    exists(h).map {
      case true  => Some(fileStore.getFileLocation(h).toFile)
      case false => None
    }

  def delete(d: Delete): Task[DeleteStatus] =
    for {
      ds1 <- futureToTask(db.removeFile(d))
      ds2 <- ds1 match {
        case Deleted      => fileStore.deleteFromStore(d.hash)
        case m @ NotFound => Task.now(m)
      }
    } yield ds2
}

object LocalStore {
  def apply(cfg: ServiceConfig)(implicit ec: ExecutionContext): Task[LocalStore] =
    for {
      db <- Task.now(new HyponomeDB(cfg.db))
      _  <- futureToTask(db.init())
      st <- Task.now(new LocalFileStore(cfg.store))
      _  <- st.init()
    } yield (new LocalStore(db, st))
}
