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

import hyponome._
import hyponome.util._
import hyponome.db.tables.{Events, Files}
import scala.concurrent.ExecutionContext
import scalaz.Scalaz._
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef
import slick.jdbc.meta.MTable
import slick.lifted.Query

trait FileDB[M[_], D] {

  def init(db: D): M[DBStatus]

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

  implicit def SQLFileDB(implicit ec: ExecutionContext): FileDB[LocalStoreM, DatabaseDef] = new FileDB[LocalStoreM, DatabaseDef] {

    type Q = Query[(Files, Events), (File, Event), Seq]

    private val files: TableQuery[Files]   = TableQuery[Files]
    private val events: TableQuery[Events] = TableQuery[Events]
    private val dummyTimestamp             = new java.sql.Timestamp(0)
    private def now: Long                  = System.currentTimeMillis

    private def create(db: DatabaseDef): LocalStoreM[Unit] =
      LocalStoreM.fromFuture[Unit](db.run(DBIO.seq((events.schema ++ files.schema).create)))

    private def exists(db: DatabaseDef): LocalStoreM[Boolean] =
      LocalStoreM.fromFuture(db.run(MTable.getTables)).map((tables: Vector[MTable]) => tables.nonEmpty)

    def init(db: DatabaseDef): LocalStoreM[DBStatus] =
      for {
        extant <- exists(db)
        status <- if (extant) DBExists.point[LocalStoreM] else create(db).map((_: Unit) => DBInitialized)
      } yield status

    def close(db: DatabaseDef): Unit = {
      val _ = db.createSession().createStatement().execute("shutdown")
      db.close
    }

    private def added(db: DatabaseDef, hash: FileHash): LocalStoreM[Boolean] = {
      val q = events.filter(_.hash === hash).sortBy(_.timestamp.desc)
      LocalStoreM.fromFuture(db.run(q.result.headOption)).map {
        case Some(Event(_, _, AddToStore, _, _, _)) => true
        case _                                      => false
      }
    }

    private def removed(db: DatabaseDef, hash: FileHash): LocalStoreM[Boolean] = {
      val q = events.filter(_.hash === hash).sortBy(_.timestamp.desc)
      LocalStoreM.fromFuture(db.run(q.result.headOption)).map {
        case Some(Event(_, _, RemoveFromStore, _, _, _)) => true
        case _                                           => false
      }
    }
    private def addEventToDB(db: DatabaseDef, e: Event): LocalStoreM[Unit] =
      LocalStoreM.fromFuture(db.run(DBIO.seq(events += e)))

    private def addFileToDB(db: DatabaseDef, f: File, e: Event): LocalStoreM[Unit] =
      LocalStoreM.fromFuture(db.run(DBIO.seq(files += f, events += e)))

    def addFile(db: DatabaseDef,
                hash: FileHash,
                name: Option[String],
                contentType: Option[String],
                length: Long,
                metadata: Option[Metadata],
                user: User,
                message: Option[Message]): LocalStoreM[AddStatus] = {
      for {
        isAdded <- added(db, hash)
        status <- {
          if (isAdded)
            Exists.point[LocalStoreM]
          else {
            val ts           = now
            val messageBytes = message.fold("".getBytes)((m: Message) => m.msg.getBytes)
            val id           = IdHash.fromBytes(hash.getBytes ++ ts.bytes ++ user.toString.getBytes ++ messageBytes)
            val f            = File(hash, name, contentType, length, metadata)
            val e            = Event(id, ts, AddToStore, hash, user, message)
            for {
              isRemoved <- removed(db, hash)
              _         <- if (isRemoved) addEventToDB(db, e) else addFileToDB(db, f, e)
            } yield Added
          }
        }
      } yield status
    }

    def removeFile(db: DatabaseDef, hash: FileHash, user: User, message: Option[Message]): LocalStoreM[RemoveStatus] = {
      for {
        isRemoved <- removed(db, hash)
        status <- {
          if (isRemoved)
            NotFound.point[LocalStoreM]
          else {
            val ts           = now
            val messageBytes = message.fold("".getBytes)((m: Message) => m.msg.getBytes)
            val id           = IdHash.fromBytes(hash.getBytes ++ ts.bytes ++ user.toString.getBytes ++ messageBytes)
            val e            = Event(id, ts, RemoveFromStore, hash, user, message)
            addEventToDB(db, e).map((_: Unit) => Removed)
          }
        }
      } yield status
    }

    def findFile(db: DatabaseDef, hash: FileHash): LocalStoreM[Option[File]] =
      for {
        isRemoved <- removed(db, hash)
        maybeFile <- {
          if (isRemoved)
            None.point[LocalStoreM]
          else {
            val files: TableQuery[Files] = TableQuery[Files]
            val q                        = files.filter(_.hash === hash)
            LocalStoreM.fromFuture(db.run(q.result.headOption))
          }
        }
      } yield maybeFile

    def countFiles(db: DatabaseDef): LocalStoreM[Long] = {
      val q = files.length
      LocalStoreM.fromFuture(db.run(q.result)).map(_.longValue)
    }

    @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
    def query(db: DatabaseDef, q: StoreQuery): LocalStoreM[Seq[StoreQueryResponse]] =
      q match {
        case StoreQuery(None, None, None, None, None, None, None, _, _) =>
          Seq.empty[StoreQueryResponse].point[LocalStoreM]
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
          LocalStoreM.fromFuture(db.run(composedQuery.result)).map { r =>
            r.map {
              case (f: File, e: Event) =>
                StoreQueryResponse(e.id, e.timestamp, e.operation, e.user, f.hash, f.name, f.contentType, f.length, f.metadata)
            }
          }
      }
  }
}
