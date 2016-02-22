package hyponome.file

import hyponome.core._
import java.io.{File => JFile}
import java.nio.file.{Files, FileSystems, Path}
import scala.concurrent.{blocking, ExecutionContext, Future}

trait HyponomeFile {

  val storePath: Path

  def getFilePath(h: SHA256Hash): Path = {
    val (dir, file) = h.value.splitAt(2)
    storePath.resolve(dir).resolve(file).toAbsolutePath
  }

  def copyToStore(hash: SHA256Hash, source: Path)(implicit ec: ExecutionContext): Future[Path] =
    Future {
      blocking { Files.copy(source, getFilePath(hash)) }
    }
}
