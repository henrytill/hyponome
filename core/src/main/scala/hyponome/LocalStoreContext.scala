/*
 * Copyright 2017 Henry Till
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

import java.nio.file.{Files, Path}
import scalaz.concurrent.Task
import slick.driver.SQLiteDriver.api._
import slick.driver.SQLiteDriver.backend.DatabaseDef

final case class LocalStoreContext(dbDef: DatabaseDef, dbSchemaVersion: DBSchemaVersion, rootPath: Path, storePath: Path)

object LocalStoreContext {

  private def existsAndIsDirectory(p: Path): Boolean =
    Files.exists(p) && Files.isDirectory(p)

  private def existsAndIsFile(p: Path): Boolean =
    Files.exists(p) && Files.isRegularFile(p)

  private def createOrRead(p: Path)(exists: Boolean): Task[DBSchemaVersion] =
    if (exists) {
      DBSchemaVersion.fromFile(p)
    } else {
      currentSchemaVersion.toFile(p).map((_: Unit) => currentSchemaVersion)
    }

  private def getSchemaVersion(p: Path): Task[DBSchemaVersion] =
    Task.now(existsAndIsFile(p)).flatMap(createOrRead(p))

  def fromPath(p: Path): Task[LocalStoreContext] =
    for {
      storeDir            <- Task.now(Files.createDirectories(p.resolve("store")))
      dbDir               <- Task.now(Files.createDirectories(p.resolve("var/hyponome/db")))
      dbFile              <- Task.now(dbDir.resolve("db.sqlite"))
      dbSchemaVersionFile <- Task.now(dbDir.resolve("schema"))
      dbSchemaVersion     <- getSchemaVersion(dbSchemaVersionFile)
      dbDef               <- Task.now(Database.forURL(url = s"jdbc:sqlite:$dbFile", keepAliveConnection = true))
    } yield LocalStoreContext(dbDef, dbSchemaVersion, p, storeDir)
}
