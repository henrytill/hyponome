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

import hyponome._
import java.nio.file.{Files, Path}
import scalaz.Scalaz._

trait FileStore[M[_], S] {

  def init(store: S): M[FileStoreStatus]

  def findFile(store: S, hash: FileHash): M[Option[S]]

  def addFile(store: S, hash: FileHash, file: S): M[AddStatus]

  def removeFile(store: S, hash: FileHash): M[RemoveStatus]
}

object FileStore {

  implicit object LocalFileStore extends FileStore[LocalStoreM, Path] {

    def fileStoreExists(store: Path): LocalStoreM[Boolean] =
      LocalStoreM.fromCanThrow(Files.exists(store))

    def createFileStore(store: Path): LocalStoreM[Path] =
      LocalStoreM.fromCanThrow(Files.createDirectories(store))

    def init(store: Path): LocalStoreM[FileStoreStatus] =
      for {
        exists <- fileStoreExists(store)
        status <- {
          if (exists)
            FileStoreExists.point[LocalStoreM]
          else
            createFileStore(store).map((_: Path) => FileStoreCreated)
        }
      } yield status

    private def fileExists(p: Path): LocalStoreM[Boolean] =
      LocalStoreM.fromCanThrow(Files.exists(p))

    private def resolvePath(store: Path, hash: FileHash): LocalStoreM[Path] = {
      val (dir, file) = hash.toString.splitAt(2)
      store.resolve(dir).resolve(file).toAbsolutePath.point[LocalStoreM]
    }

    def findFile(store: Path, hash: FileHash): LocalStoreM[Option[Path]] =
      for {
        path   <- resolvePath(store, hash)
        exists <- fileExists(path)
      } yield if (exists) Some(path) else None

    def addFile(store: Path, hash: FileHash, file: Path): LocalStoreM[AddStatus] =
      for {
        destination <- resolvePath(store, hash)
        _           <- LocalStoreM.fromCanThrow(Files.createDirectories(destination.getParent))
        exists      <- fileExists(destination)
        status <- {
          if (!exists) LocalStoreM.fromCanThrow(Files.copy(file, destination)).map((_: Path) => Added) else Exists.point[LocalStoreM]
        }
      } yield status

    def removeFile(store: Path, hash: FileHash): LocalStoreM[RemoveStatus] =
      for {
        exists <- findFile(store, hash)
        status <- {
          if (exists.isEmpty)
            NotFound.point[LocalStoreM]
          else
            for {
              path <- resolvePath(store, hash)
              _    <- LocalStoreM.fromCanThrow(Files.delete(path))
            } yield Removed
        }
      } yield status
  }
}
