package hyponome.db

import java.lang.SuppressWarnings
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Try}
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef
import slick.lifted.{Rep, Query}
import slick.jdbc.meta.MTable

import hyponome.core._
import hyponome.db.Events._

@SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
trait HyponomeDB {

  val files: TableQuery[Files]

  val events: TableQuery[Events]

  val db: DatabaseDef

  private val dummyTimestamp = new java.sql.Timestamp(0)

  val counter: AtomicLong

  def create(): Future[Unit] = {
    val s = DBIO.seq((events.schema ++ files.schema).create)
    db.run(s)
  }

  def exists(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = MTable.getTables
    db.run(q).map { ts => !ts.isEmpty }
  }

  def dumpFiles: Future[Seq[File]] = {
    db.run(files.result)
  }

  def dumpEvents: Future[Seq[Event]] = {
    db.run(events.result)
  }

  def added(hash: SHA256Hash)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = events.filter(_.hash === hash).sortBy(_.tx.desc)
    db.run(q.result.headOption).map {
      case Some(Event(_, _, Add, _, _)) => true
      case _                            => false
    }
  }

  def removed(hash: SHA256Hash)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = events.filter(_.hash === hash).sortBy(_.tx.desc)
    db.run(q.result.headOption).map {
      case Some(Event(_, _, Remove, _, _)) => true
      case _                               => false
    }
  }

  def addFile(a: Addition)(implicit ec: ExecutionContext): Future[Unit] = a match {
    case Addition(_, hash, name, contentType, length, remoteAddress) =>
      val c = counter.incrementAndGet()
      val f = File(hash, name, contentType, length)
      val e = Event(c, dummyTimestamp, Add, hash, remoteAddress)
      added(hash) flatMap {
        case true  => Future.failed(new UnsupportedOperationException)
        case false => removed(hash).flatMap {
          case true =>
            val s = DBIO.seq(events += e)
            db.run(s)
          case false =>
            val s = DBIO.seq(files += f, events += e)
            db.run(s)
        }
      }
  }

  def removeFile(r: Removal)(implicit ec: ExecutionContext): Future[Unit] = r match {
    case Removal(hash, remoteAddress) => removed(hash).flatMap {
      case true  => Future.failed(new UnsupportedOperationException)
      case false => added(hash).flatMap {
        case false => Future.failed(new UnsupportedOperationException)
        case true  =>
          val c = counter.incrementAndGet()
          val e = Event(c, dummyTimestamp, Remove, hash, remoteAddress)
          val s = DBIO.seq(events += e)
          db.run(s)
      }
    }
  }

  def notRemovedQuery: Query[(Files, Events), (File, Event), Seq] = {
    val removes = events.filter(_.operation === (Remove: Operation))
    val adds = for {
      r <- removes
      e <- events if r.hash === e.hash && e.operation === (Add: Operation) && r.timestamp > e.timestamp
    } yield e
    val removedTxs = removes.map(_.tx) union adds.map(_.tx)
    for {
      (f, e) <- files join events on (_.hash === _.hash) if !removedTxs.filter(_ === e.tx).exists
    } yield (f, e)
  }

  def runQuery(q: DBQuery)(implicit ec: ExecutionContext): Future[Seq[DBQueryResponse]] = q match {
    case DBQuery(None, None, None, None, None, None, _, _) =>
      Future(Seq())
    case DBQuery(hash, address, txLo, txHi, timeLo, timeHi, sortBy, sortOrder) =>
      type Q = Query[(Files, Events), (File, Event), Seq]
      def filterByHash: Q => Q = { (in: Q) =>
        hash match {
          case Some(hash) => in.filter(_._1.hash === hash)
          case None       => in
        }
      }
      def filterByAddress: Q => Q = { (in: Q) =>
        address match {
          case Some(address: InetAddress) =>
            val criteriaAddress: Option[InetAddress] = Option(address)
            in.filter(r => r._2.remoteAddress === criteriaAddress)
          case None              => in
        }
      }
      def filterByTx: Q => Q = { (in: Q) =>
        (txLo, txHi) match {
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
          case (Tx, Ascending) =>
            in.sortBy(_._2.tx.asc)
          case (Tx, Descending) =>
            in.sortBy(_._2.tx.desc)
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
      val filterAndSort = filterByHash andThen filterByAddress andThen filterByTx andThen filterByTimestamp andThen sort
      val composedQuery = filterAndSort(notRemovedQuery)
      db.run(composedQuery.result).map { r =>
        r.map { case (f: File, e: Event) =>
          DBQueryResponse(e.tx, e.timestamp, e.operation, e.remoteAddress, f.hash, f.name, f.contentType, f.length)
        }
      }
  }

  def findFile(hash: SHA256Hash)(implicit ec: ExecutionContext): Future[Option[File]] =
    removed(hash).flatMap {
      case true  => Future(None)
      case false =>
        val q = files.filter(_.hash === hash)
        db.run(q.result.headOption)
    }

  def countFiles(implicit ec: ExecutionContext): Future[Long] = {
    val q = files.length
    db.run(q.result).map(_.longValue)
  }

  def maxTx: Future[Option[Long]] = {
    val q = events.map(_.tx).max
    db.run(q.result)
  }

  def syncCounter()(implicit ec: ExecutionContext): Future[Unit] =
    maxTx.flatMap {
      case Some(tx) => Future(counter.set(tx))
      case None     => Future(counter.set(0))
    }

  def close(): Unit = {
    val tmp = db.createSession().createStatement() execute "shutdown;"
    db.close
  }
}
