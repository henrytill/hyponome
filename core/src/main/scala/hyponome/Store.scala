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

package hyponome

import java.io.{File => JFile}
import java.nio.file.{Files, Path}

import fs2.Strategy
import fs2.interop.cats._
import hyponome.db.FileDB
import hyponome.file.FileStore
import slick.jdbc.SQLiteProfile.backend.DatabaseDef

trait Store[M[_], P] {

  def init(): M[StoreStatus]

  def info(hash: FileHash): M[Option[File]]

  def count(): M[Long]

  def query(q: StoreQuery): M[Seq[StoreQueryResponse]]

  def findFile(hash: FileHash): M[Option[JFile]]

  def addFile(p: P, metadata: Option[Metadata], user: User, message: Option[Message]): M[AddStatus]

  def removeFile(hash: FileHash, user: User, message: Option[Message]): M[RemoveStatus]
}

object Store {

  @SuppressWarnings(Array("org.wartremover.warts.ExplicitImplicitTypes"))
  implicit def localStoreInstance(implicit s: Strategy,
                                  fs: FileStore[LocalStore.T, Path],
                                  fdb: FileDB[LocalStore.T, DatabaseDef]): Store[LocalStore.T, Path] =
    new Store[LocalStore.T, Path] {

      def init(): LocalStore.T[StoreStatus] =
        for {
          ctx <- LocalStore.ask
          _   <- fdb.init(ctx.dbDef, ctx.dbSchemaVersion)
          _   <- fs.init(ctx.storePath)
        } yield StoreExists

      def info(hash: FileHash): LocalStore.T[Option[File]] =
        LocalStore.ask.flatMap { (ctx: LocalStoreContext) =>
          fdb.findFile(ctx.dbDef, hash)
        }

      def count(): LocalStore.T[Long] =
        LocalStore.ask.flatMap { (ctx: LocalStoreContext) =>
          fdb.countFiles(ctx.dbDef)
        }

      def query(q: StoreQuery): LocalStore.T[Seq[StoreQueryResponse]] =
        LocalStore.ask.flatMap { (ctx: LocalStoreContext) =>
          fdb.query(ctx.dbDef, q)
        }

      def findFile(hash: FileHash): LocalStore.T[Option[JFile]] =
        for {
          ctx    <- LocalStore.ask
          result <- fs.findFile(ctx.storePath, hash)
        } yield result.map(_.toFile)

      private def addToFileDB(db: DatabaseDef,
                              hash: FileHash,
                              name: Option[String],
                              contentType: Option[String],
                              length: Long,
                              metadata: Option[Metadata],
                              user: User,
                              message: Option[Message]): LocalStore.T[AddStatus] =
        fdb.addFile(db, hash, name, contentType, length, metadata, user, message)

      private def addToFileStore(store: Path, hash: FileHash, file: Path, addToDbStatus: AddStatus): LocalStore.T[AddStatus] =
        addToDbStatus match {
          case Added(h) => fs.addFile(store, h, file)
          case x        => LocalStore.pure(x)
        }

      def addFile(p: Path, metadata: Option[Metadata], user: User, message: Option[Message]): LocalStore.T[AddStatus] =
        for {
          ctx    <- LocalStore.ask
          hash   <- LocalStore.fromTask(FileHash.fromPath(p))
          name   <- LocalStore.fromCanThrow(p.getFileName).map((p: Path) => Some(p.toFile.toString))
          ctype  <- LocalStore.fromCanThrow(Option(Files.probeContentType(p)))
          length <- LocalStore.fromCanThrow(p.toFile.length)
          as1    <- addToFileDB(ctx.dbDef, hash, name, ctype, length, metadata, user, message)
          as2    <- addToFileStore(ctx.storePath, hash, p, as1)
        } yield as2

      private def removeFromFileDB(db: DatabaseDef, hash: FileHash, user: User, message: Option[Message]): LocalStore.T[RemoveStatus] =
        fdb.removeFile(db, hash, user, message)

      private def removeFromFileStore(store: Path, hash: FileHash, removeFromDbStatus: RemoveStatus): LocalStore.T[RemoveStatus] =
        removeFromDbStatus match {
          case Removed(h) => fs.removeFile(store, h)
          case x          => LocalStore.pure(x)
        }

      def removeFile(hash: FileHash, user: User, message: Option[Message]): LocalStore.T[RemoveStatus] =
        for {
          ctx <- LocalStore.ask
          rs1 <- removeFromFileDB(ctx.dbDef, hash, user, message)
          rs2 <- removeFromFileStore(ctx.storePath, hash, rs1)
        } yield rs2
    }
}
