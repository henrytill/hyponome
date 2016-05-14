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
import org.slf4j.{Logger, LoggerFactory}
import scalaz.concurrent.Task
import hyponome._
import hyponome.event._

final class LocalFileStore(storePath: Path) extends FileStore[Path] {

  val logger: Logger = LoggerFactory.getLogger(classOf[LocalFileStore])

  def exists(): Task[Boolean] = Task(Files.exists(storePath))

  def create(): Task[Unit] =
    Task(Files.createDirectories(storePath)).map((_: Path) => ())

  def existsInStore(p: Path): Task[Boolean] = Task(Files.exists(p))

  def getFileLocation(h: SHA256Hash): Path = {
    val (dir, file) = h.value.splitAt(2)
    storePath.resolve(dir).resolve(file).toAbsolutePath
  }

  def add(a: Add): Task[AddStatus] =
    Task {
      val destination: Path = getFileLocation(a.hash)
      val parent: Path = Files.createDirectories(destination.getParent)
      Files.copy(a.file, destination)
    }.map { (_: Path) =>
      Added
    }.handle {
      case _: java.nio.file.FileAlreadyExistsException => Exists
    }

  def remove(hash: SHA256Hash): Task[RemoveStatus] =
    Task {
      val p: Path = getFileLocation(hash)
      Files.delete(p)
    }.map { (_: Unit) =>
      Removed
    }.handle {
      case _: java.nio.file.NoSuchFileException => NotFound
    }

  def init(): Task[Unit] = create().map { (_: Unit) =>
    logger.info(s"Using store at ${storePath.toAbsolutePath}")
  }
}
