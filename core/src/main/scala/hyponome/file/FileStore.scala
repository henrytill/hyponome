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

trait FileStore[M[_], S] {

  def init(store: S): M[FileStoreStatus]

  def findFile(store: S, hash: FileHash): M[Option[S]]

  def addFile(store: S, hash: FileHash, file: S): M[AddStatus]

  def removeFile(store: S, hash: FileHash): M[RemoveStatus]
}

object FileStore {

  implicit def LocalFileStore(implicit s: Strategy): FileStore[LocalStore.T, Path] = new FileStore[LocalStore.T, Path] {

    def fileStoreExists(store: Path): LocalStore.T[Boolean] =
      LocalStore.fromCanThrow(Files.exists(store))

    def createFileStore(store: Path): LocalStore.T[Path] =
      LocalStore.fromCanThrow(Files.createDirectories(store))

    def init(store: Path): LocalStore.T[FileStoreStatus] =
      fileStoreExists(store).flatMap { (exists: Boolean) =>
        if (exists)
          LocalStore.pure(FileStoreExists)
        else
          createFileStore(store).map((_: Path) => FileStoreCreated)
      }

    private def fileExists(p: Path): LocalStore.T[Boolean] =
      LocalStore.fromCanThrow(Files.exists(p))

    private def resolvePath(store: Path, hash: FileHash): LocalStore.T[Path] = {
      val (dir, file) = hash.toString.splitAt(2)
      LocalStore.pure(store.resolve(dir).resolve(file).toAbsolutePath)
    }

    def findFile(store: Path, hash: FileHash): LocalStore.T[Option[Path]] =
      for {
        path   <- resolvePath(store, hash)
        exists <- fileExists(path)
      } yield if (exists) Some(path) else None

    def addFile(store: Path, hash: FileHash, file: Path): LocalStore.T[AddStatus] = {
      def add(exists: Boolean, destination: Path): LocalStore.T[AddStatus] =
        if (!exists)
          LocalStore.fromCanThrow(Files.copy(file, destination)).map((_: Path) => Added(hash))
        else
          LocalStore.pure(Exists(hash))
      for {
        destination <- resolvePath(store, hash)
        _           <- LocalStore.fromCanThrow(Files.createDirectories(destination.getParent))
        exists      <- fileExists(destination)
        status      <- add(exists, destination)
      } yield status
    }

    def removeFile(store: Path, hash: FileHash): LocalStore.T[RemoveStatus] = {
      def remove(maybePath: Option[Path]): LocalStore.T[RemoveStatus] =
        if (maybePath.isEmpty)
          LocalStore.pure(NotFound(hash))
        else
          resolvePath(store, hash).flatMap { (path: Path) =>
            LocalStore.fromCanThrow(Files.delete(path)).map { (_: Unit) =>
              Removed(hash)
            }
          }
      findFile(store, hash).flatMap { (maybePath: Option[Path]) =>
        remove(maybePath)
      }
    }
  }
}
