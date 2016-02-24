package hyponome.actor

import akka.actor._
import com.typesafe.config.Config
import hyponome.core._
import java.nio.file.Path

object AskActor {
  // Creating a store
  final case object Create
  final case object CreateAck
  final case object CreateFail

  // Deleting a store
  final case object Delete
  final case object DeleteAck
  final case object DeleteFail

  // Adding files
  final case class AdditionAck(addition: Addition)
  final case class AdditionFail(addition: Addition)

  // Removing files
  final case class RemovalAck(removal: Removal)
  final case class RemovalFail(removal: Removal)

  // Finding a file
  final case class Result(file: Option[Path])

  def props(dbActor: ActorRef, fileActor: ActorRef): Props =
    Props(new AskActor(dbActor, fileActor))
}

class AskActor(dbActor: ActorRef, fileActor: ActorRef) extends Actor {

  import AskActor._

  @SuppressWarnings(Array(
    "org.brianmckenna.wartremover.warts.Any",
    "org.brianmckenna.wartremover.warts.Nothing"
  ))
  def prime: Receive = {
    // Creating a store
    case Create =>
      dbActor   ! DBActor.CreateDB(sender)
    case DBActor.CreateDBAck(c: ActorRef) =>
      fileActor ! FileActor.CreateStore(c)
    case FileActor.CreateStoreAck(c: ActorRef) =>
      c ! CreateAck
    // Deleting a store
    case Delete =>
      fileActor ! FileActor.DeleteStore(sender)
    case FileActor.DeleteStoreAck(c: ActorRef) =>
      c ! DeleteAck
    // Adding files
    case a: Addition =>
      dbActor ! DBActor.AddFile(sender, a)
    case DBActor.AddFileAck(c: ActorRef, a: Addition) =>
      fileActor ! FileActor.AddFile(c, a)
    case FileActor.AddFileAck(c: ActorRef, a: Addition) =>
      c ! AdditionAck(a)
    case DBActor.AddFileFail(c: ActorRef, a: Addition, e: Throwable) =>
      c ! AdditionFail(a)
    case FileActor.AddFileFail(c: ActorRef, a: Addition, e: Throwable) =>
      c ! AdditionFail(a)
    // Removing files
    case r: Removal  =>
      dbActor ! DBActor.RemoveFile(sender, r)
    case DBActor.RemoveFileAck(c: ActorRef, r: Removal) =>
      c ! RemovalAck(r)
    case DBActor.RemoveFileFail(c: ActorRef, r: Removal, e: Throwable) =>
      c ! RemovalFail(r)
    // Finding a file
    case FindFile(h: SHA256Hash)  =>
      dbActor ! DBActor.FindFile(sender, h)
    case DBActor.DBFile(c: ActorRef, h: SHA256Hash, None) =>
      c ! Result(None)
    case DBActor.DBFile(c: ActorRef, h: SHA256Hash, Some(_: hyponome.core.File)) =>
      fileActor ! FileActor.FindFile(c, h)
    case FileActor.StoreFile(c: ActorRef, h: SHA256Hash, f: Option[Path]) =>
      c ! Result(f)
    // And more ...
    case _ =>
  }

  def receive: Receive = prime
}
