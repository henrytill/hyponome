package hyponome

import java.net.URI
import java.nio.file.{Files, Path}
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import scala.concurrent.{blocking, ExecutionContext, Future}

package object core {

  private def withInputStream[T](path: Path)(op: java.io.InputStream => T): T = {
    val fist = Files.newInputStream(path)
    try {
      op(fist)
    }
    finally fist.close()
  }

  def getSHA256Hash(p: Path)(implicit ec: ExecutionContext): Future[SHA256Hash] =
    Future {
      blocking {
        val s: String = withInputStream(p)(sha256Hex)
        SHA256Hash(s)
      }
    }

  def getURI(hostname: String, port: Int, hash: SHA256Hash, name: Option[String]): URI =
    name match {
      case Some(n) => new URI("http", s"//$hostname:$port/objects/$hash/$n", null)
      case None    => new URI("http", s"//$hostname:$port/objects/$hash"   , null)
    }
}
