package hyponome.actor

import akka.actor.{Actor, ActorRef, Props}
import java.nio.file.{FileSystem, FileSystems, Path}
import java.util.concurrent.atomic.AtomicLong
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.core._

object Receptionist {

  final case class AddFile(client: ActorRef, addition: Addition)

  final case class RemoveFile(client: ActorRef, removal: Removal)

  final case class FindFile(client: ActorRef, hash: SHA256Hash, name: Option[String])

  final case class GetInfo(client: ActorRef)

  def props(db: Function0[DatabaseDef], store: Path): Props = Props(new Receptionist(db, store))
}

class Receptionist(db: Function0[DatabaseDef], store: Path) extends Actor {

  import Receptionist._

  val counter: AtomicLong = new AtomicLong()

  val dbActor   = context.actorOf(DBActor.props(db, counter))
  val fileActor = context.actorOf(FileActor.props(store))

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any"))
  def prime: Receive = {
    // Adding files
    case a: Addition =>
      fileActor ! AddFile(sender, a)
    case FileActor.AddFileAck(c: ActorRef, a: Addition) =>
      dbActor ! AddFile(c, a)
    case FileActor.PreviouslyAddedFile(c: ActorRef, a: Addition) =>
      dbActor ! AddFile(c, a)
    case FileActor.AddFileFail(c: ActorRef, a: Addition, e: Throwable) =>
      c ! AdditionFail(a, e)
    case DBActor.AddFileAck(c: ActorRef, a: Addition) =>
      c ! AdditionAck(a)
    case DBActor.PreviouslyAddedFile(c: ActorRef, a: Addition) =>
      c ! PreviouslyAdded(a)
    case DBActor.AddFileFail(c: ActorRef, a: Addition, e: Throwable) =>
      c ! AdditionFail(a, e)
    // Removing files
    case r: Removal =>
      dbActor ! RemoveFile(sender, r)
    case DBActor.RemoveFileAck(c: ActorRef, r: Removal) =>
      c ! RemovalAck(r)
    case DBActor.PreviouslyRemovedFile(c: ActorRef, r: Removal) =>
      c ! PreviouslyRemoved(r)
    case DBActor.RemoveFileFail(c: ActorRef, r: Removal, e: Throwable) =>
      c ! RemovalFail(r, e)
    // Finding a file
    case h: SHA256Hash  =>
      dbActor ! FindFile(sender, h, None)
    case DBActor.DBFile(c: ActorRef, h: SHA256Hash, Some(f: File)) =>
      fileActor ! FindFile(c, h, f.name)
    case DBActor.DBFile(c: ActorRef, h: SHA256Hash, None) =>
      c ! Result(None, None)
    case FileActor.StoreFile(c: ActorRef, h: SHA256Hash, f: Option[Path], n: Option[String]) =>
      c ! Result(f, n)
    // GetInfo
    case Objects =>
      dbActor ! GetInfo(sender)
    case DBActor.DBInfo(c: ActorRef, count: Long, max: Long) =>
      val storeName = store.toFile.toString
      c ! Info(storeName, count, max)
    // Query
    case q: DBQuery =>
      dbActor forward q
  }

  def receive: Receive = prime
}
