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

package hyponome.file

import java.nio.file.{Files, Path}
import scalaz.concurrent.Task
import hyponome.core._

final class LocalFileStore(storePath: Path) extends FileStore[Path] {

  def exists(): Task[Boolean] = Task { Files.exists(storePath) }

  def create(): Task[Unit] =
    Task {
      Files.createDirectories(storePath)
    }.map((_: Path) => ())

  def existsInStore(p: Path): Task[Boolean] = Task { Files.exists(p) }

  def getFileLocation(h: SHA256Hash): Path = {
    val (dir, file) = h.value.splitAt(2)
    storePath.resolve(dir).resolve(file).toAbsolutePath
  }

  def copyToStore(hash: SHA256Hash, source: Path): Task[PostStatus] =
    Task {
      val destination: Path = getFileLocation(hash)
      val parent: Path = Files.createDirectories(destination.getParent)
      Files.copy(source, destination)
    }.map { (_: Path) =>
      Created
    }.handle {
      case _: java.nio.file.FileAlreadyExistsException => Exists
    }

  def deleteFromStore(hash: SHA256Hash): Task[DeleteStatus] =
    Task {
      val p: Path = getFileLocation(hash)
      Files.delete(p)
    }.map { (_: Unit) =>
      Deleted
    }.handle {
      case _: java.nio.file.NoSuchFileException => NotFound
    }
}
