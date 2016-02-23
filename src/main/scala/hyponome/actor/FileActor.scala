package hyponome.actor

import akka.actor._
import hyponome.core._
import hyponome.file._
import java.nio.file.Path
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object FileActor {
  // Creating a store
  final case object CreateStore
  final case object CreateStoreAck
  final case object CreateStoreFail

  // Deleting a store
  final case object DeleteStore
  final case object DeleteStoreAck
  final case object DeleteStoreFail

  // Adding files
  final case class AddFile(hash: SHA256Hash, path: Path)
  final case class AddFileAck(hash: SHA256Hash, path: Path)
  final case class AddFileFail(hash: SHA256Hash, path: Path, exception: Throwable)

  // Removing files
  final case class RemoveFile(h: SHA256Hash)
  final case class RemoveFileAck(h: SHA256Hash)
  final case class RemoveFileFail(h: SHA256Hash, exception: Throwable)

  // Finding a file
  final case class FindFile(h: SHA256Hash)
  final case class StoreFile(h: SHA256Hash, p: Option[Path])

  def props(p: Path): Props = Props(new FileActor(p))
}

class FileActor(p: Path) extends Actor with HyponomeFile {

  import context.dispatcher
  import FileActor._

  val storePath: Path = p

  @SuppressWarnings(Array(
    "org.brianmckenna.wartremover.warts.Any",
    "org.brianmckenna.wartremover.warts.Nothing"
  ))
  def prime: Receive = {
    case CreateStore =>
      val replyToRef: ActorRef = sender
      this.createStore() match {
        case Success(_: Path) => replyToRef ! CreateStoreAck
        case Failure(_)       => replyToRef ! CreateStoreFail
      }
    case DeleteStore =>
      val replyToRef: ActorRef = sender
      this.deleteStore() match {
        case Success(_: Path) => replyToRef ! DeleteStoreAck
        case Failure(_)       => replyToRef ! DeleteStoreFail
      }
    case AddFile(h: SHA256Hash, p: Path) =>
      val replyToRef: ActorRef = sender
      val hashPath = this.getFilePath(h)
      val copyFut: Future[Path] = this.existsInStore(hashPath).flatMap {
        case false => this.copyToStore(h, p)
        case true  => Future.failed(new UnsupportedOperationException)
      }
      copyFut onComplete {
        case Success(_: Path)      => replyToRef ! AddFileAck(h, p)
        case Failure(e: Throwable) => replyToRef ! AddFileFail(h, p, e)
      }
    case RemoveFile(h: SHA256Hash) =>
      val replyToRef: ActorRef = sender
      val deleteFut: Future[Unit] = this.deleteFromStore(h)
      deleteFut onComplete {
        case Success(_: Unit) => replyToRef ! RemoveFileAck(h)
        case Failure(e)       => replyToRef ! RemoveFileFail(h, e)
      }
    case FindFile(h: SHA256Hash) =>
      val replyToRef: ActorRef = sender
      val possiblePath: Path = this.getFilePath(h)
      val existsFut: Future[Boolean] = this.existsInStore(possiblePath)
      existsFut onComplete {
        case Success(true)  => replyToRef ! StoreFile(h, Some(possiblePath))
        case Success(false) => replyToRef ! StoreFile(h, None)
        case Failure(_)     => replyToRef ! StoreFile(h, None)
      }
    case _ =>
  }

  def receive: Receive = prime
}
