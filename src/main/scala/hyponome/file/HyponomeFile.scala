package hyponome.file

import hyponome.core._
import java.nio.file._
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import scala.concurrent.{blocking, ExecutionContext, Future}

trait HyponomeFile {

  val storePath: Path

  def createStore()(implicit ec: ExecutionContext): Future[Path] =
    Future {
      blocking { Files.createDirectories(storePath) }
    }

  private def withInputStream[T](path: Path)(op: java.io.InputStream => T): T = {
    val fist = Files.newInputStream(path)
    try {
      op(fist)
    }
    finally fist.close()
  }

  def getSHA256Hash(p: Path): SHA256Hash = {
    val s: String = withInputStream(p)(sha256Hex)
    SHA256Hash(s)
  }

  def getFilePath(h: SHA256Hash): Path = {
    val (dir, file) = h.value.splitAt(2)
    storePath.resolve(dir).resolve(file).toAbsolutePath
  }

  def copyToStore(hash: SHA256Hash, source: Path)(implicit ec: ExecutionContext): Future[Path] =
    Future {
      blocking {
        val destination: Path = getFilePath(hash)
        val parent: Path = Files.createDirectory(destination.getParent)
        Files.copy(source, destination)
      }
    }

  private def makeSimpleFileVisitor(f: Path => Unit): SimpleFileVisitor[Path] =
    new SimpleFileVisitor[Path] {
      override def visitFile(p: Path, attrs: attribute.BasicFileAttributes): FileVisitResult = {
        f(p)
        FileVisitResult.CONTINUE
      }
    }

  private def deletePathAndParent(p: Path): Unit = {
    Files.delete(p)
    Files.delete(p.getParent)
  }

  private def recursiveDeletePath(p: Path): Unit = {
    val r: Path = Files.walkFileTree(p, makeSimpleFileVisitor(deletePathAndParent))
    Files.delete(p)
  }

  def deleteStore()(implicit ec: ExecutionContext): Future[Unit] =
    Future {
      blocking { recursiveDeletePath(storePath) }
    }
}
