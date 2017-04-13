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

  implicit object LocalFileStore extends FileStore[LocalStore.T, Path] {

    def fileStoreExists(store: Path): LocalStore.T[Boolean] =
      LocalStore.fromCanThrow(Files.exists(store))

    def createFileStore(store: Path): LocalStore.T[Path] =
      LocalStore.fromCanThrow(Files.createDirectories(store))

    def init(store: Path): LocalStore.T[FileStoreStatus] =
      for {
        exists <- fileStoreExists(store)
        status <- {
          if (exists)
            FileStoreExists.point[LocalStore.T]
          else
            createFileStore(store).map((_: Path) => FileStoreCreated)
        }
      } yield status

    private def fileExists(p: Path): LocalStore.T[Boolean] =
      LocalStore.fromCanThrow(Files.exists(p))

    private def resolvePath(store: Path, hash: FileHash): LocalStore.T[Path] = {
      val (dir, file) = hash.toString.splitAt(2)
      store.resolve(dir).resolve(file).toAbsolutePath.point[LocalStore.T]
    }

    def findFile(store: Path, hash: FileHash): LocalStore.T[Option[Path]] =
      for {
        path   <- resolvePath(store, hash)
        exists <- fileExists(path)
      } yield if (exists) Some(path) else None

    def addFile(store: Path, hash: FileHash, file: Path): LocalStore.T[AddStatus] =
      for {
        destination <- resolvePath(store, hash)
        _           <- LocalStore.fromCanThrow(Files.createDirectories(destination.getParent))
        exists      <- fileExists(destination)
        status <- {
          if (!exists) LocalStore.fromCanThrow(Files.copy(file, destination)).map((_: Path) => Added(hash))
          else Exists(hash).point[LocalStore.T]
        }
      } yield status

    def removeFile(store: Path, hash: FileHash): LocalStore.T[RemoveStatus] =
      for {
        exists <- findFile(store, hash)
        status <- {
          if (exists.isEmpty)
            NotFound(hash).point[LocalStore.T]
          else
            for {
              path <- resolvePath(store, hash)
              _    <- LocalStore.fromCanThrow(Files.delete(path))
            } yield Removed(hash)
        }
      } yield status
  }
}
