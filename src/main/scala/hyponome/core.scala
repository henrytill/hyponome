package hyponome

import java.net.InetAddress
import java.nio.file.{Files, Path}
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.{BaseColumnType, MappedColumnType}

object core {
  final case class SHA256Hash(value: String) {
    override def toString: String = value
  }

  object SHA256Hash {
    implicit val SHA256HashColumnType: BaseColumnType[SHA256Hash] =
      MappedColumnType.base[SHA256Hash, String](
        { case SHA256Hash(v: String) => v   },
        { case (v: String) => SHA256Hash(v) }
      )
  }

  sealed trait Operation
  case object Add extends Operation
  case object Remove extends Operation

  object Operation {
    implicit val operationColumnType: BaseColumnType[Operation] =
      MappedColumnType.base[Operation, Int](
        { case Add => 1; case Remove => -1 },
        { case 1 => Add; case -1 => Remove }
      )
  }

  final case class Addition(
    file: Path,
    hash: SHA256Hash,
    name: String,
    contentType: String,
    length: Long,
    remoteAddress: Option[InetAddress]
  )

  final case class Removal(
    hash: SHA256Hash,
    remoteAddress: Option[InetAddress]
  )

  final case class File(
    hash: SHA256Hash,
    name: String,
    contentType: String,
    length: Long
  )

  final case class Event(
    id: Long,
    timestamp: java.sql.Timestamp,
    operation: Operation,
    hash: SHA256Hash,
    remoteAddress: Option[InetAddress]
  )

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
