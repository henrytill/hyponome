package hyponome.actor

import akka.actor.{Actor, ActorRef, Props}
import java.nio.file.{FileSystem, FileSystems, Path}
import java.util.concurrent.atomic.AtomicLong
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.core._

object Receptionist {
  def props(db: DatabaseDef, store: Path): Props = Props(new Receptionist(db, store))
}

class Receptionist(db: DatabaseDef, store: Path) extends Actor {

  val counter: AtomicLong = new AtomicLong()

  val dbActor   = context.actorOf(DBActor.props(db, counter))
  val fileActor = context.actorOf(FileActor.props(store))

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any"))
  def prime: Receive = {
    // Adding files
    case a: Addition =>
      fileActor ! FileActor.AddFile(sender, a)
    case FileActor.AddFileAck(c: ActorRef, a: Addition) =>
      dbActor ! DBActor.AddFile(c, a)
    case FileActor.PreviouslyAddedFile(c: ActorRef, a: Addition) =>
      dbActor ! DBActor.AddFile(c, a)
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
      dbActor ! DBActor.RemoveFile(sender, r)
    case DBActor.RemoveFileAck(c: ActorRef, r: Removal) =>
      c ! RemovalAck(r)
    case DBActor.PreviouslyRemovedFile(c: ActorRef, r: Removal) =>
      c ! PreviouslyRemoved(r)
    case DBActor.RemoveFileFail(c: ActorRef, r: Removal, e: Throwable) =>
      c ! RemovalFail(r, e)
    // Finding a file
    case FindFile(h: SHA256Hash)  =>
      dbActor ! DBActor.FindFile(sender, h)
    case DBActor.DBFile(c: ActorRef, h: SHA256Hash, Some(_: File)) =>
      fileActor ! FileActor.FindFile(c, h)
    case DBActor.DBFile(c: ActorRef, h: SHA256Hash, None) =>
      c ! Result(None)
    case FileActor.StoreFile(c: ActorRef, h: SHA256Hash, f: Option[Path]) =>
      c ! Result(f)
  }

  def receive: Receive = prime
}