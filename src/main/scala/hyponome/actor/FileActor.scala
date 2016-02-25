package hyponome.actor

import akka.actor.{Actor, ActorRef, Props}
import hyponome.core._
import hyponome.file._
import java.nio.file.Path
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object FileActor {
  // Creating a file store
  final case class CreateStore(client: ActorRef)
  final case class CreateStoreAck(client: ActorRef)
  final case class CreateStoreFail(client: ActorRef)

  // Deleting a file store
  final case class DeleteStore(client: ActorRef)
  final case class DeleteStoreAck(client: ActorRef)
  final case class DeleteStoreFail(client: ActorRef)

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
  final case class StoreFile(
    client: ActorRef,
    hash: SHA256Hash,
    file: Option[Path]
  )

  def props(p: Path): Props = Props(new FileActor(p))
}

class FileActor(p: Path) extends Actor with HyponomeFile {

  import context.dispatcher
  import FileActor._

  val storePath: Path = p

  @SuppressWarnings(Array(
    "org.brianmckenna.wartremover.warts.Any",
    "org.brianmckenna.wartremover.warts.AsInstanceOf",
    "org.brianmckenna.wartremover.warts.IsInstanceOf",
    "org.brianmckenna.wartremover.warts.Nothing"
  ))
  def prime: Receive = {
    case CreateStore(c: ActorRef) =>
      val replyToRef: ActorRef = sender
      this.createStore() match {
        case Success(_: Path) => replyToRef ! CreateStoreAck(c)
        case Failure(_)       => replyToRef ! CreateStoreFail(c)
      }
    case DeleteStore(c: ActorRef) =>
      val replyToRef: ActorRef = sender
      this.deleteStore() match {
        case Success(_: Path) => replyToRef ! DeleteStoreAck(c)
        case Failure(_)       => replyToRef ! DeleteStoreFail(c)
      }
    case AddFile(c: ActorRef, a: Addition) =>
      val replyToRef: ActorRef = sender
      val hashPath = this.getFilePath(a.hash)
      val copyFut: Future[Path] = this.existsInStore(hashPath).flatMap {
        case false => this.copyToStore(a.hash, a.file)
        case true  => Future.failed(new UnsupportedOperationException)
      }
      copyFut onComplete {
        case Success(_: Path) =>
          replyToRef ! AddFileAck(c, a)
        case Failure(_: UnsupportedOperationException) =>
          replyToRef ! PreviouslyAddedFile(c, a)
        case Failure(e: Throwable) =>
          replyToRef ! AddFileFail(c, a, e)
      }
    case RemoveFile(c: ActorRef, r: Removal) =>
      val replyToRef: ActorRef = sender
      val deleteFut: Future[Unit] = this.deleteFromStore(r.hash)
      deleteFut onComplete {
        case Success(_: Unit) =>
          replyToRef ! RemoveFileAck(c, r)
        case Failure(_: java.nio.file.NoSuchFileException) =>
          replyToRef ! PreviouslyRemovedFile(c, r)
        case Failure(e: Throwable) =>
          replyToRef ! RemoveFileFail(c, r, e)
      }
    case FindFile(c: ActorRef, h: SHA256Hash) =>
      val replyToRef: ActorRef = sender
      val possiblePath: Path = this.getFilePath(h)
      val existsFut: Future[Boolean] = this.existsInStore(possiblePath)
      existsFut onComplete {
        case Success(true)  => replyToRef ! StoreFile(c, h, Some(possiblePath))
        case Success(false) => replyToRef ! StoreFile(c, h, None)
        case Failure(_)     => replyToRef ! StoreFile(c, h, None)
      }
    case _ =>
  }

  def receive: Receive = prime
}
