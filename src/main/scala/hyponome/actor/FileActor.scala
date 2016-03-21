package hyponome.actor

import akka.actor.{Actor, ActorRef, Props, Stash}
import akka.pattern.{pipe, PipeableFuture}
import java.nio.file.Path
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import hyponome.core._
import hyponome.file._
import Controller.{PostWr, DeleteWr, GetWr}

object FileActor {

  final case object Ready

  sealed trait PostResponseWr extends Product with Serializable
  final case class PostAckWr(client: ActorRef, post: Post, status: PostStatus) extends PostResponseWr
  final case class PostFailWr(client: ActorRef, post: Post, e: Throwable) extends PostResponseWr

  sealed trait DeleteResponseWr extends Product with Serializable
  final case class DeleteAckWr(client: ActorRef, delete: Delete, status: DeleteStatus) extends DeleteResponseWr
  final case class DeleteFailWr(client: ActorRef, delete: Delete, e: Throwable) extends DeleteResponseWr

  final case class ResultWr(client: ActorRef, file: Option[Path], name: Option[String])

  def props(p: Path): Props = Props(new FileActor(p))
}

class FileActor(p: Path) extends Actor with Stash with HyponomeFile {

  import context.dispatcher
  import FileActor._

  val logger: Logger = LoggerFactory.getLogger(classOf[FileActor])

  val storePath: Path = p

  override def preStart(): Unit = {
    val selfRef: ActorRef = self
    createStore() onComplete {
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
    case PostWr(c: ActorRef, p: Post) =>
      val copyFut: Future[PostResponseWr] = copyToStore(p.hash, p.file).map {
        case Created => PostAckWr(c, p, Created)
        case Exists  => PostAckWr(c, p, Exists)
      }.recover { case ex => PostFailWr(c, p, ex) }
      val tmp: PipeableFuture[PostResponseWr] = pipe(copyFut) to sender
    case DeleteWr(c: ActorRef, d: Delete) =>
      val deleteFut: Future[DeleteResponseWr] = deleteFromStore(d.hash).map {
        case Deleted  => DeleteAckWr(c, d, Deleted)
        case NotFound => DeleteAckWr(c, d, NotFound)
      }.recover { case ex => DeleteFailWr(c, d, ex) }
      val tmp: PipeableFuture[DeleteResponseWr] = pipe(deleteFut) to sender
    case GetWr(c: ActorRef, h: SHA256Hash, n: Option[String]) =>
      val possiblePath: Path = getFilePath(h)
      val existsFut: Future[ResultWr] = existsInStore(possiblePath).map {
        case true  => ResultWr(c, Some(possiblePath), n)
        case false => ResultWr(c, None, None)
      }
      val tmp: PipeableFuture[ResultWr] = pipe(existsFut) to sender
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
