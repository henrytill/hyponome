package hyponome

import java.lang.SuppressWarnings
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Try}
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef

final case class Addition(
  hash: SHA256Hash,
  name: String,
  contentType: String,
  length: Long,
  remoteAddress: String
)

final case class Removal(
  hash: SHA256Hash,
  remoteAddress: String
)

trait HyponomeDB {

  val files: TableQuery[Files]

  val events: TableQuery[Events]

  val db: DatabaseDef

  val dummyTimestamp = new java.sql.Timestamp(0)

  def createDB: Future[Unit] = {
    val s = DBIO.seq((events.schema ++ files.schema).create)
    db.run(s)
  }

  def dumpFiles: Future[Seq[File]] = {
    db.run(files.result)
  }

  def dumpEvents: Future[Seq[Event]] = {
    db.run(events.result)
  }

  def addFile(a: Addition): Future[Unit] = a match {
    case Addition(hash, name, contentType, length, remoteAddress) =>
      val f = File(hash, name, contentType, length)
      val e = Event(0L, dummyTimestamp, Add, hash, remoteAddress)
      val s = DBIO.seq(files += f, events += e)
      db.run(s)
  }

  def removed(hash: SHA256Hash)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = events.filter(_.hash === hash).sortBy(_.tx.desc)
    db.run(q.result.head).map {
      case Event(_, _, Remove, _, _) => true
      case _                         => false
    }
  }

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
  def removeFile(r: Removal)(implicit ec: ExecutionContext): Future[Unit] = r match {
    case Removal(hash, remoteAddress) =>
      removed(hash).flatMap {
        case true  => Future.failed(new UnsupportedOperationException)
        case false =>
          val e = Event(0L, dummyTimestamp, Remove, hash, remoteAddress)
          val s = DBIO.seq(events += e)
          db.run(s)
      }
  }

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
  def findFile(hash: SHA256Hash)(implicit ec: ExecutionContext): Future[File] =
    removed(hash).flatMap {
      case true  => Future.failed(new IllegalArgumentException)
      case false =>
        val q = files.filter(_.hash === hash)
        db.run(q.result.head)
    }

  def countFiles: Future[Int] = {
    val q = files.length
    db.run(q.result)
  }

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.NonUnitStatements"))
  def close(): Unit = {
    db.createSession().createStatement() execute "shutdown;"
    db.close
  }
}
