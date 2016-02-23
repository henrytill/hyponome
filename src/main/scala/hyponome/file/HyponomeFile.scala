package hyponome.file

import hyponome.core._
import java.nio.file._
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import scala.concurrent.{blocking, ExecutionContext, Future}
import scala.util.{Success, Try}

trait HyponomeFile {

  val storePath: Path

  def createStore(): Try[Path] = Try(Files.createDirectories(storePath))

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

  private def makeDeleteFileVisitor: SimpleFileVisitor[Path] =
    new SimpleFileVisitor[Path] {
      override def visitFile(p: Path, attrs: attribute.BasicFileAttributes): FileVisitResult = {
        Files.delete(p)
        FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(p: Path, e: java.io.IOException): FileVisitResult = {
        Files.delete(p)
        FileVisitResult.CONTINUE
      }
    }

  private def recursiveDeletePath(p: Path): Path =
    Files.walkFileTree(p, makeDeleteFileVisitor)

  def deleteStore(): Try[Path] =
    if (Files.exists(storePath)) Try(recursiveDeletePath(storePath))
    else Success(storePath)
}
