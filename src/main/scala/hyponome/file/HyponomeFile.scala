package hyponome.file

import hyponome.core._
import java.nio.file._
import scala.concurrent.{blocking, ExecutionContext, Future}
import scala.util.{Success, Try}

trait HyponomeFile {

  val storePath: Path

  def createStore()(implicit ec: ExecutionContext): Future[Path] =
    Future {
      blocking {
        Files.createDirectories(storePath)
      }
    }

  def getFilePath(h: SHA256Hash): Path = {
    val (dir, file) = h.value.splitAt(2)
    storePath.resolve(dir).resolve(file).toAbsolutePath
  }

  def existsInStore(p: Path)(implicit ec: ExecutionContext): Future[Boolean] =
    Future {
      blocking {
        Files.exists(p)
      }
    }

  def copyToStore(hash: SHA256Hash, source: Path)(implicit ec: ExecutionContext): Future[PostStatus] = 
    Future {
      blocking {
        val destination: Path = getFilePath(hash)
        val parent: Path = Files.createDirectories(destination.getParent)
        Files.copy(source, destination)
      }
    }.map { (p: Path) =>
      Created
    }.recover {
      case e: java.nio.file.FileAlreadyExistsException => Exists
    }

  def deleteFromStore(hash: SHA256Hash)(implicit ec: ExecutionContext): Future[DeleteStatus] =
    Future {
      blocking {
        val p: Path = getFilePath(hash)
        Files.delete(p)
      }
    }.map { (_: Unit) =>
      Deleted
    }.recover {
      case e: java.nio.file.NoSuchFileException => NotFound
    }
}
