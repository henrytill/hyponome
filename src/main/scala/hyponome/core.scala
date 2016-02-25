package hyponome

import java.net.InetAddress
import java.nio.file.{Files, Path}
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.{BaseColumnType, MappedColumnType}

object core {
  // Core message API
  final case object Create
  final case object Delete

  sealed trait CreateResponse
  final case object CreateAck extends CreateResponse
  final case object CreateFail extends CreateResponse

  sealed trait DeleteResponse
  final case object DeleteAck extends DeleteResponse
  final case object DeleteFail extends DeleteResponse

  final case class Addition(
    file: Path,
    hash: SHA256Hash,
    name: String,
    contentType: String,
    length: Long,
    remoteAddress: Option[InetAddress]
  )

  sealed trait AdditionResponse
  final case class AdditionAck(addition: Addition) extends AdditionResponse
  final case class AdditionFail(addition: Addition) extends AdditionResponse
  final case class PreviouslyAdded(addition: Addition) extends AdditionResponse

  final case class Removal(
    hash: SHA256Hash,
    remoteAddress: Option[InetAddress]
  )

  sealed trait RemovalResponse
  final case class RemovalAck(removal: Removal) extends RemovalResponse
  final case class RemovalFail(removal: Removal) extends RemovalResponse
  final case class PreviouslyRemoved(removal: Removal) extends RemovalResponse

  final case class FindFile(h: SHA256Hash)
  final case class Result(file: Option[Path])

  // Core types
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

  // DB types
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

  // DB row types
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
