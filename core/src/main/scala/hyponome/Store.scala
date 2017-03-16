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

import hyponome.db.FileDB
import hyponome.file.FileStore
import java.io.{File => JFile}
import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext
import scalaz.Scalaz._
import slick.driver.H2Driver.backend.DatabaseDef

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
  implicit def localStore(implicit ec: ExecutionContext,
                          fs: FileStore[LocalStoreM, Path],
                          fdb: FileDB[LocalStoreM, DatabaseDef]): Store[LocalStoreM, Path] =
    new Store[LocalStoreM, Path] {

      def init(): LocalStoreM[StoreStatus] =
        for {
          ctx       <- LocalStoreM.ask
          fdbStatus <- fdb.init(ctx.dbDef)
          fsStatus  <- fs.init(ctx.storePath)
          status    <- StoreExists.point[LocalStoreM]
        } yield status

      def info(hash: FileHash): LocalStoreM[Option[File]] =
        for {
          ctx       <- LocalStoreM.ask
          maybeFile <- fdb.findFile(ctx.dbDef, hash)
        } yield maybeFile

      def count(): LocalStoreM[Long] =
        for {
          ctx   <- LocalStoreM.ask
          count <- fdb.countFiles(ctx.dbDef)
        } yield count

      def query(q: StoreQuery): LocalStoreM[Seq[StoreQueryResponse]] =
        for {
          ctx    <- LocalStoreM.ask
          result <- fdb.query(ctx.dbDef, q)
        } yield result

      def findFile(hash: FileHash): LocalStoreM[Option[JFile]] =
        for {
          ctx    <- LocalStoreM.ask
          result <- fs.findFile(ctx.storePath, hash)
        } yield result.map(_.toFile)

      private def getContentType(p: Path): LocalStoreM[String] =
        LocalStoreM.fromCanThrow(Files.probeContentType(p))

      private def addToFileDB(db: DatabaseDef,
                              hash: FileHash,
                              name: Option[String],
                              contentType: Option[String],
                              length: Long,
                              metadata: Option[Metadata],
                              user: User,
                              message: Option[Message]): LocalStoreM[AddStatus] =
        fdb.addFile(db, hash, name, contentType, length, metadata, user, message)

      private def addToFileStore(store: Path, hash: FileHash, file: Path, addToDbStatus: AddStatus): LocalStoreM[AddStatus] =
        addToDbStatus match {
          case Added => fs.addFile(store, hash, file)
          case x     => x.point[LocalStoreM]
        }

      def addFile(p: Path, metadata: Option[Metadata], user: User, message: Option[Message]): LocalStoreM[AddStatus] = {
        for {
          ctx    <- LocalStoreM.ask
          hash   <- LocalStoreM.fromTask(FileHash.fromPath(p))
          name   <- LocalStoreM.fromCanThrow(p.getFileName).map((p: Path) => Some(p.toFile.toString))
          ctype  <- LocalStoreM.fromCanThrow(Option(Files.probeContentType(p)))
          length <- LocalStoreM.fromCanThrow(p.toFile.length)
          as1    <- addToFileDB(ctx.dbDef, hash, name, ctype, length, metadata, user, message)
          as2    <- addToFileStore(ctx.storePath, hash, p, as1)
        } yield as2
      }

      /*
       * def exists(h: FileHash): LocalStoreM[Boolean] =
       *  for {
       *    ctx       <- LocalStoreM.ask
       *    maybeFile <- info(h)
       *    result <- {
       *      if (maybeFile.isEmpty)
       *        false.point[LocalStoreM]
       *      else
       *        fs.findFile(ctx.storePath, h).map(_.isDefined)
       *    }
       * } yield result
       */

      private def removeFromFileDB(db: DatabaseDef, hash: FileHash, user: User, message: Option[Message]): LocalStoreM[RemoveStatus] =
        fdb.removeFile(db, hash, user, message)

      private def removeFromFileStore(store: Path, hash: FileHash, removeFromDbStatus: RemoveStatus): LocalStoreM[RemoveStatus] =
        removeFromDbStatus match {
          case Removed => fs.removeFile(store, hash)
          case x       => x.point[LocalStoreM]
        }

      def removeFile(hash: FileHash, user: User, message: Option[Message]): LocalStoreM[RemoveStatus] =
        for {
          ctx <- LocalStoreM.ask
          rs1 <- removeFromFileDB(ctx.dbDef, hash, user, message)
          rs2 <- removeFromFileStore(ctx.storePath, hash, rs1)
        } yield rs2
    }
}
