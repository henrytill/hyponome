package hyponome.actor

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.config.{Config, ConfigFactory}
import hyponome.core._
import java.nio.file.{FileSystem, FileSystems, Path}
import slick.driver.H2Driver.api.Database
import slick.driver.H2Driver.backend.DatabaseDef

object Receptionist {
  // Creating a file store
  final case class Create(client: ActorRef)

  // Deleting a file store
  final case class Delete(client: ActorRef)

  // Adding files
  final case class AddFile(client: ActorRef, addition: Addition)

  // Removing files
  final case class RemoveFile(client: ActorRef, removal: Removal)

  // Finding a file
  final case class FindFile(client: ActorRef, hash: SHA256Hash)

  def props(db: DatabaseDef, store: Path): Props = Props(new Receptionist(db, store))
}

class Receptionist(db: DatabaseDef, store: Path) extends Actor {

  import Receptionist._

  val dbActor   = context.actorOf(DBActor.props(db))
  val fileActor = context.actorOf(FileActor.props(store))

  @SuppressWarnings(Array(
    "org.brianmckenna.wartremover.warts.Any",
    "org.brianmckenna.wartremover.warts.Nothing"
  ))
  def prime: Receive = {
    // Creating a store
    case Create(c: ActorRef) =>
      fileActor ! FileActor.CreateStore(c)
    case FileActor.CreateStoreAck(c: ActorRef) =>
      dbActor ! DBActor.CreateDB(c)
    case DBActor.CreateDBAck(c: ActorRef) =>
      c ! CreateAck
    // Deleting a store
    case Delete(c: ActorRef) =>
      fileActor ! FileActor.DeleteStore(c)
    case FileActor.DeleteStoreAck(c: ActorRef) =>
      c ! DeleteAck
    // Adding files
    case AddFile(c: ActorRef, a: Addition) =>
      fileActor ! FileActor.AddFile(c, a)
    case FileActor.AddFileFail(c: ActorRef, a: Addition, e: Throwable) =>
      c ! AdditionFail(a)
    case FileActor.AddFileAck(c: ActorRef, a: Addition) =>
      dbActor ! DBActor.AddFile(c, a)
    case DBActor.AddFileFail(c: ActorRef, a: Addition, e: Throwable) =>
      c ! AdditionFail(a)
    case DBActor.AddFileAck(c: ActorRef, a: Addition) =>
      c ! AdditionAck(a)
    // Removing files
    case RemoveFile(c: ActorRef, r: Removal)  =>
      dbActor ! DBActor.RemoveFile(c, r)
    case DBActor.RemoveFileFail(c: ActorRef, r: Removal, e: Throwable) =>
      c ! RemovalFail(r)
    case DBActor.RemoveFileAck(c: ActorRef, r: Removal) =>
      c ! RemovalAck(r)
    // Finding a file
    case FindFile(c: ActorRef, h: SHA256Hash)  =>
      dbActor ! DBActor.FindFile(c, h)
    case DBActor.DBFile(c: ActorRef, h: SHA256Hash, Some(_: File)) =>
      fileActor ! FileActor.FindFile(c, h)
    case DBActor.DBFile(c: ActorRef, h: SHA256Hash, None) =>
      c ! Result(None)
    case FileActor.StoreFile(c: ActorRef, h: SHA256Hash, f: Option[Path]) =>
      c ! Result(f)
    // And more ...
    case _ =>
  }

  def receive: Receive = prime
}
