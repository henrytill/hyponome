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

package hyponome.db

import fs2.Strategy
import fs2.interop.cats._
import hyponome._
import hyponome.util._
import hyponome.db.tables.{Events, Files}
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.SQLiteProfile.api._
import slick.jdbc.SQLiteProfile.backend.DatabaseDef
import slick.jdbc.meta.MTable
import slick.lifted.Query

trait FileDB[M[_], D] {

  def init(db: D, schemaVersion: DBSchemaVersion): M[DBStatus]

  def close(db: D): Unit

  def findFile(db: D, hash: FileHash): M[Option[File]]

  def addFile(db: D,
              hash: FileHash,
              name: Option[String],
              contentType: Option[String],
              length: Long,
              metadata: Option[Metadata],
              user: User,
              message: Option[Message]): M[AddStatus]

  def removeFile(db: D, hash: FileHash, user: User, message: Option[Message]): M[RemoveStatus]

  def countFiles(db: D): M[Long]

  def query(db: D, q: StoreQuery): M[Seq[StoreQueryResponse]]
}

object FileDB {

  implicit def SQLFileDB(implicit ec: ExecutionContext, s: Strategy): FileDB[LocalStore.T, DatabaseDef] =
    new FileDB[LocalStore.T, DatabaseDef] {

      type Q = Query[(Files, Events), (File, Event), Seq]

      private val files: TableQuery[Files]   = TableQuery[Files]
      private val events: TableQuery[Events] = TableQuery[Events]
      private def now(): Long                = System.currentTimeMillis

      private def create(db: DatabaseDef): LocalStore.T[Unit] =
        LocalStore.fromFuture[Unit](db.run(DBIO.seq((events.schema ++ files.schema).create)))

      private def exists(db: DatabaseDef): LocalStore.T[Boolean] =
        LocalStore.fromFuture(db.run(MTable.getTables)).map((tables: Vector[MTable]) => tables.nonEmpty)

      private def runMigrations(db: DatabaseDef, schemaVersion: DBSchemaVersion): Future[Unit] =
        Future(())

      private def migrate(db: DatabaseDef, schemaVersion: DBSchemaVersion): LocalStore.T[DBStatus] =
        LocalStore.fromFuture(runMigrations(db, schemaVersion)).map((_: Unit) => DBExists)

      @SuppressWarnings(Array("org.wartremover.warts.Equals"))
      private def isCurrent(schemaVersion: DBSchemaVersion): Boolean =
        schemaVersion == currentSchemaVersion

      def init(db: DatabaseDef, schemaVersion: DBSchemaVersion): LocalStore.T[DBStatus] =
        for {
          extant <- exists(db)
          status <- {
            if (!extant) {
              create(db).map((_: Unit) => DBInitialized)
            } else if (isCurrent(schemaVersion)) {
              LocalStore.pure(DBExists)
            } else {
              migrate(db, schemaVersion)
            }
          }
        } yield status

      def close(db: DatabaseDef): Unit = db.close

      private def added(db: DatabaseDef, hash: FileHash): LocalStore.T[Boolean] = {
        val q = events.filter(_.hash === hash).sortBy(_.timestamp.desc)
        LocalStore.fromFuture(db.run(q.result.headOption)).map {
          case Some(Event(_, _, AddToStore, _, _, _)) => true
          case _                                      => false
        }
      }

      private def removed(db: DatabaseDef, hash: FileHash): LocalStore.T[Boolean] = {
        val q = events.filter(_.hash === hash).sortBy(_.timestamp.desc)
        LocalStore.fromFuture(db.run(q.result.headOption)).map {
          case Some(Event(_, _, RemoveFromStore, _, _, _)) => true
          case _                                           => false
        }
      }

      private def addEventToDB(db: DatabaseDef, e: Event): LocalStore.T[Unit] =
        LocalStore.fromFuture(db.run(DBIO.seq(events += e)))

      private def addFileToDB(db: DatabaseDef, f: File, e: Event): LocalStore.T[Unit] =
        LocalStore.fromFuture(db.run(DBIO.seq(files += f, events += e)))

      def addFile(db: DatabaseDef,
                  hash: FileHash,
                  name: Option[String],
                  contentType: Option[String],
                  length: Long,
                  metadata: Option[Metadata],
                  user: User,
                  message: Option[Message]): LocalStore.T[AddStatus] = {
        for {
          isAdded <- added(db, hash)
          status <- {
            if (isAdded)
              LocalStore.pure(Exists(hash))
            else {
              val ts = now()
              val mb = message.fold("".getBytes)((m: Message) => m.msg.getBytes)
              val id = IdHash.fromBytes(hash.getBytes ++ ts.bytes ++ user.toString.getBytes ++ mb)
              val f  = File(hash, name, contentType, length, metadata)
              val e  = Event(id, ts, AddToStore, hash, user, message)
              for {
                isRemoved <- removed(db, hash)
                _         <- if (isRemoved) addEventToDB(db, e) else addFileToDB(db, f, e)
              } yield Added(hash)
            }
          }
        } yield status
      }

      def removeFile(db: DatabaseDef, hash: FileHash, user: User, message: Option[Message]): LocalStore.T[RemoveStatus] = {
        for {
          isRemoved <- removed(db, hash)
          status <- {
            if (isRemoved)
              LocalStore.pure(NotFound(hash))
            else {
              val ts = now()
              val mb = message.fold("".getBytes)((m: Message) => m.msg.getBytes)
              val id = IdHash.fromBytes(hash.getBytes ++ ts.bytes ++ user.toString.getBytes ++ mb)
              val e  = Event(id, ts, RemoveFromStore, hash, user, message)
              addEventToDB(db, e).map((_: Unit) => Removed(hash))
            }
          }
        } yield status
      }

      def findFile(db: DatabaseDef, hash: FileHash): LocalStore.T[Option[File]] =
        for {
          isRemoved <- removed(db, hash)
          maybeFile <- {
            if (isRemoved)
              LocalStore.pure(None)
            else {
              val files: TableQuery[Files] = TableQuery[Files]
              val q                        = files.filter(_.hash === hash)
              LocalStore.fromFuture(db.run(q.result.headOption))
            }
          }
        } yield maybeFile

      def countFiles(db: DatabaseDef): LocalStore.T[Long] = {
        val q = files.length
        LocalStore.fromFuture(db.run(q.result)).map(_.longValue)
      }

      @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
      def query(db: DatabaseDef, q: StoreQuery): LocalStore.T[Seq[StoreQueryResponse]] =
        q match {
          case StoreQuery(None, None, None, None, None, None, None, _, _) =>
            LocalStore.pure(Seq.empty[StoreQueryResponse])
          case StoreQuery(hash, name, user, txLo, txHi, timeLo, timeHi, sortBy, sortOrder) =>
            def notRemoved: Q = {
              val removes = events.filter(_.operation === (RemoveFromStore: Operation))
              val adds = for {
                r <- removes
                e <- events if r.hash === e.hash && e.operation === (AddToStore: Operation) &&
                  r.timestamp > e.timestamp
              } yield e
              val removedIds = removes.map(_.id) union adds.map(_.id)
              for {
                (f, e) <- files join events on (_.hash === _.hash)
                if !removedIds.filter(_ === e.id).exists
              } yield (f, e)
            }
            def filterByHash: Q => Q = { (in: Q) =>
              hash match {
                case Some(h) => in.filter(_._1.hash === h)
                case None    => in
              }
            }
            def filterByName: Q => Q = { (in: Q) =>
              name match {
                case Some(n) => in.filter(_._1.name like s"%$n%")
                case None    => in
              }
            }
            def filterByUser: Q => Q = { (in: Q) =>
              user match {
                case Some(user: User) =>
                  val criteriaUser: Option[User] = Option(user)
                  in.filter(r => r._2.user === criteriaUser)
                case None => in
              }
            }
            def filterByTimestamp: Q => Q = { (in: Q) =>
              (timeLo, timeHi) match {
                case (Some(lo), Some(hi)) =>
                  in.filter(_._2.timestamp >= timeLo).filter(_._2.timestamp <= timeHi)
                case (Some(lo), None) =>
                  in.filter(_._2.timestamp >= timeLo)
                case (None, Some(hi)) =>
                  in.filter(_._2.timestamp <= timeHi)
                case (None, None) =>
                  in
              }
            }
            def sort: Q => Q = { (in: Q) =>
              (sortBy, sortOrder) match {
                case (SortByName, Ascending) =>
                  in.sortBy(_._1.name.asc)
                case (SortByName, Descending) =>
                  in.sortBy(_._1.name.desc)
                case (SortByTime, Ascending) =>
                  in.sortBy(_._2.timestamp.asc)
                case (SortByTime, Descending) =>
                  in.sortBy(_._2.timestamp.desc)
                case (SortByUser, Ascending) =>
                  in.sortBy(_._2.user.asc)
                case (SortByUser, Descending) =>
                  in.sortBy(_._2.user.desc)
              }
            }
            val filterAndSort =
              filterByHash andThen filterByName andThen filterByUser andThen filterByTimestamp andThen sort
            val composedQuery = filterAndSort(notRemoved)
            LocalStore.fromFuture(db.run(composedQuery.result)).map { r =>
              r.map {
                case (f: File, e: Event) =>
                  StoreQueryResponse(e.id, e.timestamp, e.operation, e.user, f.hash, f.name, f.contentType, f.length, f.metadata)
              }
            }
        }
    }
}
