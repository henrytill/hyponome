package hyponome.actor

import akka.actor.{Actor, ActorRef, Props, Stash}
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.Future
import scala.util.{Success, Failure}
import slick.driver.H2Driver.api.TableQuery
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.core._
import hyponome.db._

object DBActor {

  final case object Ready

  final case class AddFile(client: ActorRef, addition: Addition)
  final case class AddFileAck(client: ActorRef, addition: Addition)
  final case class AddFileFail(client: ActorRef, addition: Addition, e: Throwable)
  final case class PreviouslyAddedFile(client: ActorRef, addition: Addition)

  final case class RemoveFile(client: ActorRef, removal: Removal)
  final case class RemoveFileAck(client: ActorRef, removal: Removal)
  final case class RemoveFileFail(client: ActorRef, removal: Removal, e: Throwable)
  final case class PreviouslyRemovedFile(client: ActorRef, removal: Removal)

  final case class FindFile(client: ActorRef, hash: SHA256Hash)
  final case class DBFile(
    client: ActorRef,
    hash: SHA256Hash,
    file: Option[hyponome.core.File]
  )

  final case object CountFiles
  final case class Count(c: Int)

  final case object DumpFiles
  final case class FileDump(fs: Seq[File])

  final case object DumpEvents
  final case class EventDump(es: Seq[Event])

  def props(dbDef: DatabaseDef, count: AtomicLong): Props = Props(new DBActor(dbDef, count))
}

class DBActor(dbDef: DatabaseDef, count: AtomicLong) extends Actor with Stash with HyponomeDB {

  import context.dispatcher
  import DBActor._

  val logger: Logger = LoggerFactory.getLogger(classOf[DBActor])

  val files: TableQuery[Files] = TableQuery[Files]

  val events: TableQuery[Events] = TableQuery[Events]

  val db: DatabaseDef = dbDef

  val counter: AtomicLong = count

  override def preStart(): Unit = {
    val selfRef: ActorRef = self
    val initFut: Future[Unit] = this.exists.flatMap {
      case true  => this.syncCounter()
      case false => this.createDB
    }
    initFut onComplete {
      case Success(_: Unit) =>
        logger.info("DB Initialized")
        self ! Ready
      case Failure(ex) =>
        logger.error(ex.getMessage)
    }
  }

  override def postStop(): Unit = {
    this.close()
  }

  @SuppressWarnings(Array(
    "org.brianmckenna.wartremover.warts.Any",
    "org.brianmckenna.wartremover.warts.IsInstanceOf"
  ))
  def prime: Receive = {
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

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any"))
  def pre: Receive = {
    case Ready =>
      unstashAll()
      context.become(prime)
    case msg => stash()
  }

  def receive: Receive = pre
}
