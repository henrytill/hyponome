package hyponome

import java.nio.file.{Files, Path}
import org.apache.commons.codec.digest.DigestUtils.sha256Hex

package object core {
  // Core functions
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
}
