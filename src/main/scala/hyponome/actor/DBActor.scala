package hyponome.actor

import akka.actor._
import hyponome.db._
import scala.concurrent.Future
import scala.util.{Success, Failure}
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef

object DBActor {
  // Creating a db
  final case object CreateDB
  final case object CreateDBAck
  final case object CreateDBFail

  // Adding files
  final case class AddFile(f: Addition)
  final case object AddFileAck
  final case object AddFileFail

  // Removing files
  final case class RemoveFile(r: Removal)
  final case object RemoveFileAck
  final case object RemoveFileFail

  // Finding a file
  final case class FindFile(h: SHA256Hash)
  final case object FileNotFound

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
    "org.brianmckenna.wartremover.warts.NonUnitStatements",
    "org.brianmckenna.wartremover.warts.Product",
    "org.brianmckenna.wartremover.warts.Serializable"
  ))
  def prime: Receive = {
    case CreateDB =>
      val replyToRef: ActorRef = sender
      val createFut: Future[Unit] = this.createDB
      createFut onComplete {
        case Success(_: Unit) => replyToRef ! CreateDBAck
        case Failure(_)       => replyToRef ! CreateDBFail
      }
    case AddFile(f: Addition) =>
      val replyToRef: ActorRef = sender
      val addFut: Future[Unit] = this.addFile(f)
      addFut onComplete {
        case Success(_: Unit) => replyToRef ! AddFileAck
        case Failure(_)       => replyToRef ! AddFileFail
      }
    case RemoveFile(r: Removal) =>
      val replyToRef: ActorRef = sender
      val removeFut: Future[Unit] = this.removeFile(r)
      removeFut onComplete {
        case Success(_: Unit) => replyToRef ! RemoveFileAck
        case Failure(_)       => replyToRef ! RemoveFileFail
      }
    case FindFile(h: SHA256Hash) =>
      val replyToRef: ActorRef = sender
      val findFut: Future[File] = this.findFile(h)
      findFut onComplete {
        case Success(f: File) => replyToRef ! f
        case Failure(_)       => replyToRef ! FileNotFound
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
