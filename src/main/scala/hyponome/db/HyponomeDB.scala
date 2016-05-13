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

package hyponome.db

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef
import slick.jdbc.meta.MTable
import slick.lifted.Query
import hyponome._
import hyponome.db.tables._
import hyponome.db.tables.Events._
import hyponome.query._
import hyponome.event._

final class HyponomeDB(dbConfig: Function0[DatabaseDef])(implicit ec: ExecutionContext) {

  type Q = Query[(Files, Events), (File, Event), Seq]

  val tx: AtomicLong                     = new AtomicLong()
  private val logger: Logger             = LoggerFactory.getLogger(classOf[HyponomeDB])
  private val db: DatabaseDef            = dbConfig()
  private val files: TableQuery[Files]   = TableQuery[Files]
  private val events: TableQuery[Events] = TableQuery[Events]
  private val dummyTimestamp             = new java.sql.Timestamp(0)

  // Lifecycle helper methods

  private def create(): Future[Unit] = {
    val s = DBIO.seq((events.schema ++ files.schema).create)
    db.run(s)
  }

  private def exists: Future[Boolean] = {
    val q = MTable.getTables
    db.run(q).map { ts => ts.nonEmpty }
  }

  private def maxTx: Future[Option[Long]] = {
    val q = events.map(_.tx).max
    db.run(q.result)
  }

  private def syncCounter(): Future[Unit] =
    maxTx.flatMap {
      case Some(x) => Future(tx.set(x))
      case None    => Future(tx.set(0))
    }

  // Main lifecycle methods

  def init(): Future[Unit] = {
    exists.flatMap {
      case true  => this.syncCounter()
      case false => this.create()
    }.map { (_: Unit) =>
      logger.info("DB Initialized")
    }
  }

  def close(): Unit = {
    val tmp = db.createSession().createStatement() execute "shutdown;"
    db.close
  }

  // API helper methods

  private def added(hash: SHA256Hash): Future[Boolean] = {
    val q = events.filter(_.hash === hash).sortBy(_.tx.desc)
    db.run(q.result.headOption).map {
      case Some(Event(_, _, AddToStore, _, _)) => true
      case _                                   => false
    }
  }

  private def removed(hash: SHA256Hash): Future[Boolean] = {
    val q = events.filter(_.hash === hash).sortBy(_.tx.desc)
    db.run(q.result.headOption).map {
      case Some(Event(_, _, RemoveFromStore, _, _)) => true
      case _                                        => false
    }
  }

  // Main API methods

  def addFile(a: Add): Future[AddStatus] = a match {
    case Add(_, _, _, hash, name, contentType, length, remoteAddress) =>
      added(hash) flatMap {
        case true  => Future(Exists)
        case false =>
          val c = tx.incrementAndGet()
          val f = File(hash, name, contentType, length)
          val e = Event(c, dummyTimestamp, AddToStore, hash, remoteAddress)
          removed(hash).flatMap {
            case true =>
              val s = DBIO.seq(events += e)
              db.run(s)
            case false =>
              val s = DBIO.seq(files += f, events += e)
              db.run(s)
          }.map(_ => Created)
      }
  }

  def removeFile(r: Delete): Future[DeleteStatus] = r match {
    case Delete(hash, remoteAddress) =>
      removed(hash).flatMap {
        case true  => Future(NotFound)
        case false => added(hash).flatMap {
          case false => Future(NotFound)
          case true  =>
            val c = tx.incrementAndGet()
            val e = Event(c, dummyTimestamp, RemoveFromStore, hash, remoteAddress)
            val s = DBIO.seq(events += e)
            db.run(s).map(_ => Deleted)
        }
      }
  }

  def find(hash: SHA256Hash): Future[Option[File]] =
    removed(hash).flatMap {
      case true  => Future(None)
      case false =>
        val q = files.filter(_.hash === hash)
        db.run(q.result.headOption)
    }

  def countFiles: Future[Long] = {
    val q = files.length
    db.run(q.result).map(_.longValue)
  }

  def runQuery(q: StoreQuery): Future[Seq[StoreQueryResponse]] = q match {
    case StoreQuery(None, None, None, None, None, None, None, _, _) =>
      Future(Seq())
    case StoreQuery(hash, name, address, txLo, txHi, timeLo, timeHi, sortBy, sortOrder) =>
      def notRemoved: Q = {
        val removes = events.filter(_.operation === (RemoveFromStore: Operation))
        val adds = for {
          r <- removes
          e <- events if r.hash === e.hash && e.operation === (AddToStore: Operation) && r.timestamp > e.timestamp
        } yield e
        val removedTxs = removes.map(_.tx) union adds.map(_.tx)
        for {
          (f, e) <- files join events on (_.hash === _.hash) if !removedTxs.filter(_ === e.tx).exists
        } yield (f, e)
      }
      def filterByHash: Q => Q = {
        (in: Q) => hash match {
          case Some(h) => in.filter(_._1.hash === h)
          case None    => in
        }
      }
      def filterByName: Q => Q = {
        (in: Q) => name match {
          case Some(n) => in.filter(_._1.name like s"%$n%")
          case None    => in
        }
      }
      def filterByAddress: Q => Q = {
        (in: Q) => address match {
          case Some(address: InetAddress) =>
            val criteriaAddress: Option[InetAddress] = Option(address)
            in.filter(r => r._2.remoteAddress === criteriaAddress)
          case None              => in
        }
      }
      def filterByTx: Q => Q = {
        (in: Q) => (txLo, txHi) match {
          case (Some(lo), Some(hi)) =>
            in.filter(_._2.tx >= txLo).filter(_._2.tx <= txHi)
          case (Some(lo), None) =>
            in.filter(_._2.tx >= txLo)
          case (None, Some(hi)) =>
            in.filter(_._2.tx <= txHi)
          case (None, None) =>
            in
        }
      }
      def filterByTimestamp: Q => Q = {
        (in: Q) => (timeLo, timeHi) match {
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
      def sort: Q => Q = {
        (in: Q) => (sortBy, sortOrder) match {
          case (Tx, Ascending) =>
            in.sortBy(_._2.tx.asc)
          case (Tx, Descending) =>
            in.sortBy(_._2.tx.desc)
          case (Name, Ascending) =>
            in.sortBy(_._1.name.asc)
          case (Name, Descending) =>
            in.sortBy(_._1.name.desc)
          case (Time, Ascending) =>
            in.sortBy(_._2.timestamp.asc)
          case (Time, Descending) =>
            in.sortBy(_._2.timestamp.desc)
          case (Address, Ascending) =>
            in.sortBy(_._2.remoteAddress.asc)
          case (Address, Descending) =>
            in.sortBy(_._2.remoteAddress.desc)
        }
      }
      val filterAndSort = filterByHash andThen filterByName andThen filterByAddress andThen filterByTx andThen filterByTimestamp andThen sort
      val composedQuery = filterAndSort(notRemoved)
      db.run(composedQuery.result).map { r =>
        r.map { case (f: File, e: Event) =>
          StoreQueryResponse(e.tx, e.timestamp, e.operation, e.remoteAddress, f.hash, f.name, f.contentType, f.length)
        }
      }
  }
}
