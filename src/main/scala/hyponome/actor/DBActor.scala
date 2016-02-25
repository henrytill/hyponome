package hyponome.actor

import akka.actor.{Actor, ActorRef, Props}
import hyponome.core._
import hyponome.db._
import org.h2.jdbc.JdbcSQLException
import scala.concurrent.Future
import scala.util.{Success, Failure}
import slick.driver.H2Driver.api.{Database, TableQuery}
import slick.driver.H2Driver.backend.DatabaseDef

object DBActor {
  // Creating a db
  final case class CreateDB(client: ActorRef)
  final case class CreateDBAck(client: ActorRef)
  final case class CreateDBFail(client: ActorRef)

  // Adding files
  final case class AddFile(client: ActorRef, addition: Addition)
  final case class AddFileAck(client: ActorRef, addition: Addition)
  final case class AddFileFail(client: ActorRef, addition: Addition, e: Throwable)
  final case class PreviouslyAddedFile(client: ActorRef, addition: Addition)

  // Removing files
  final case class RemoveFile(client: ActorRef, removal: Removal)
  final case class RemoveFileAck(client: ActorRef, removal: Removal)
  final case class RemoveFileFail(client: ActorRef, removal: Removal, e: Throwable)
  final case class PreviouslyRemovedFile(client: ActorRef, removal: Removal)

  // Finding a file
  final case class FindFile(client: ActorRef, hash: SHA256Hash)
  final case class DBFile(
    client: ActorRef,
    hash: SHA256Hash,
    file: Option[hyponome.core.File]
  )

  // Counting files
  final case object CountFiles
  final case class Count(c: Int)

  final case object DumpFiles
  final case class FileDump(fs: Seq[File])

  final case object DumpEvents
  final case class EventDump(es: Seq[Event])

  def props(dbDef: DatabaseDef): Props = Props(new DBActor(dbDef))
}

class DBActor(dbDef: DatabaseDef) extends Actor with HyponomeDB {

  import context.dispatcher
  import DBActor._

  val files: TableQuery[Files] = TableQuery[Files]

  val events: TableQuery[Events] = TableQuery[Events]

  val db: DatabaseDef = dbDef

  override def postStop(): Unit = {
    this.close()
  }

  @SuppressWarnings(Array(
    "org.brianmckenna.wartremover.warts.Any",
    "org.brianmckenna.wartremover.warts.AsInstanceOf",
    "org.brianmckenna.wartremover.warts.IsInstanceOf",
    "org.brianmckenna.wartremover.warts.NonUnitStatements",
    "org.brianmckenna.wartremover.warts.Nothing",
    "org.brianmckenna.wartremover.warts.Product",
    "org.brianmckenna.wartremover.warts.Serializable"
  ))
  def prime: Receive = {
    case CreateDB(c: ActorRef) =>
      val replyToRef: ActorRef = sender
      val createFut: Future[Unit] = this.createDB
      createFut onComplete {
        case Success(_: Unit) => replyToRef ! CreateDBAck(c)
        case Failure(_)       => replyToRef ! CreateDBFail(c)
      }
    case AddFile(c: ActorRef, f: Addition) =>
      val replyToRef: ActorRef = sender
      val addFut: Future[Unit] = this.addFile(f)
      addFut onComplete {
        case Success(_: Unit) =>
          replyToRef ! AddFileAck(c, f)
        case Failure(_: UnsupportedOperationException) =>
          replyToRef ! PreviouslyAddedFile(c, f)
        case Failure(e) =>
          replyToRef ! AddFileFail(c, f, e)
      }
    case RemoveFile(c: ActorRef, r: Removal) =>
      val replyToRef: ActorRef = sender
      val removeFut: Future[Unit] = this.removeFile(r)
      removeFut onComplete {
        case Success(_: Unit) =>
          replyToRef ! RemoveFileAck(c, r)
        case Failure(_: UnsupportedOperationException) =>
          replyToRef ! PreviouslyRemovedFile(c, r)
        case Failure(e) =>
          replyToRef ! RemoveFileFail(c, r, e)
      }
    case FindFile(c: ActorRef, h: SHA256Hash) =>
      val replyToRef: ActorRef = sender
      val findFut: Future[Option[File]] = this.findFile(h)
      findFut onComplete {
        case Success(f)    => replyToRef ! DBFile(c, h, f)
        case Failure(_)    => replyToRef ! DBFile(c, h, None)
      }
    case CountFiles =>
      val replyToRef: ActorRef = sender
      val countFut: Future[Int] = this.countFiles
      countFut onComplete {
        case Success(i: Int) => replyToRef ! Count(i)
        case _               =>
      }
    case DumpFiles =>
      val replyToRef: ActorRef = sender
      val dumpFilesFut: Future[Seq[File]] = this.dumpFiles
      dumpFilesFut onComplete {
        case Success(fs: Seq[File]) => replyToRef ! FileDump(fs)
        case _                      =>
      }
    case DumpEvents =>
      val replyToRef: ActorRef = sender
      val dumpEventsFut: Future[Seq[Event]] = this.dumpEvents
      dumpEventsFut onComplete {
        case Success(es: Seq[Event]) => replyToRef ! EventDump(es)
        case _                       =>
      }
  }

  def receive: Receive = prime
}
