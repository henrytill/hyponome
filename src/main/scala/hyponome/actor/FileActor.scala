package hyponome.actor

import akka.actor.{Actor, ActorRef, Props, Stash}
import java.nio.file.Path
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import hyponome.core._
import hyponome.file._
import Receptionist.{AddFile, RemoveFile, FindFile}

object FileActor {

  final case object Ready

  final case class AddFileAck(client: ActorRef, addition: Addition)
  final case class AddFileFail(client: ActorRef, addition: Addition, e: Throwable)
  final case class PreviouslyAddedFile(client: ActorRef, addition: Addition)

  final case class RemoveFileAck(client: ActorRef, removal: Removal)
  final case class RemoveFileFail(client: ActorRef, removal: Removal, e: Throwable)
  final case class PreviouslyRemovedFile(client: ActorRef, removal: Removal)

  final case class StoreFile(client: ActorRef, hash: SHA256Hash, file: Option[Path])

  def props(p: Path): Props = Props(new FileActor(p))
}

class FileActor(p: Path) extends Actor with Stash with HyponomeFile {

  import context.dispatcher
  import FileActor._

  val logger: Logger = LoggerFactory.getLogger(classOf[FileActor])

  val storePath: Path = p

  override def preStart(): Unit = {
    val selfRef: ActorRef = self
    this.createStore() onComplete {
      case Success(p: Path) =>
        logger.info(s"Using store at ${p.toAbsolutePath}")
        selfRef ! Ready
      case Failure(ex)      =>
        logger.error(ex.getMessage)
    }
  }

  @SuppressWarnings(Array(
    "org.brianmckenna.wartremover.warts.Any",
    "org.brianmckenna.wartremover.warts.IsInstanceOf"
  ))
  def prime: Receive = {
    case AddFile(c: ActorRef, a: Addition) =>
      val replyToRef: ActorRef = sender
      val copyFut: Future[Path] = this.copyToStore(a.hash, a.file)
      copyFut onComplete {
        case Success(_: Path) =>
          replyToRef ! AddFileAck(c, a)
        case Failure(_: java.nio.file.FileAlreadyExistsException) =>
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
