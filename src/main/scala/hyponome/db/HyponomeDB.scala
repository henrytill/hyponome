package hyponome.db

import hyponome.core._
import java.lang.SuppressWarnings
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Try}
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef
import slick.jdbc.meta.MTable

trait HyponomeDB {

  val files: TableQuery[Files]

  val events: TableQuery[Events]

  val db: DatabaseDef

  val dummyTimestamp = new java.sql.Timestamp(0)

  val counter: AtomicLong

  def createDB(): Future[Unit] = {
    val s = DBIO.seq((events.schema ++ files.schema).create)
    db.run(s)
  }

  def exists(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = MTable.getTables
    db.run(q).flatMap { ts => Future(!ts.isEmpty) }
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

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
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

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
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

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
  def findFile(hash: SHA256Hash)(implicit ec: ExecutionContext): Future[Option[File]] =
    removed(hash).flatMap {
      case true  => Future(None)
      case false =>
        val q = files.filter(_.hash === hash)
        db.run(q.result.headOption)
    }

  def countFiles: Future[Int] = {
    val q = files.length
    db.run(q.result)
  }

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
  def maxTx: Future[Option[Long]] = {
    val q = events.map(_.tx).max
    db.run(q.result)
  }

  def syncCounter()(implicit ec: ExecutionContext): Future[Unit] =
    maxTx.flatMap {
      case Some(tx) => Future(counter.set(tx))
      case None     => Future(counter.set(0))
    }

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.NonUnitStatements"))
  def close(): Unit = {
    db.createSession().createStatement() execute "shutdown;"
    db.close
  }
}
