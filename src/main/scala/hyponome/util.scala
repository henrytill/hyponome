package hyponome

import hyponome.core.SHA256Hash
import org.apache.commons.codec.digest.DigestUtils.sha256Hex

object util {

  def withFileInputStream[T](file: java.io.File)(op: java.io.FileInputStream => T): T = {
    val fist = new java.io.FileInputStream(file)
    try {
      op(fist)
    }
    finally fist.close()
  }

  def getSHA256Hash(file: java.io.File): SHA256Hash = {
    val s: String = withFileInputStream(file)(sha256Hex)
    SHA256Hash(s)
  }
}
