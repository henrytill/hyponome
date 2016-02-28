package hyponome.core

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import java.net.{InetAddress, URI}
import java.nio.file.Path
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.{BaseColumnType, MappedColumnType}
import slick.driver.H2Driver.backend.DatabaseDef

final case class HyponomeConfig(
  db: Function0[DatabaseDef],
  store: Path,
  hostname: String,
  port: Int,
  uploadKey: String
)

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

// Message API
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
final case class PreviouslyAdded(addition: Addition) extends AdditionResponse
final case class AdditionFail(addition: Addition, exception: Throwable) extends AdditionResponse

final case class Removal(
  hash: SHA256Hash,
  remoteAddress: Option[InetAddress]
)

sealed trait RemovalResponse
final case class RemovalAck(removal: Removal) extends RemovalResponse
final case class PreviouslyRemoved(removal: Removal) extends RemovalResponse
final case class RemovalFail(removal: Removal, exception: Throwable) extends RemovalResponse

final case class FindFile(h: SHA256Hash)
final case class Result(file: Option[Path])

sealed trait Status {
  def toStatusCode: StatusCode
}
final case object Created extends Status {
  def toStatusCode: StatusCode = StatusCodes.Created
}
final case object Exists extends Status {
  def toStatusCode: StatusCode = StatusCodes.OK
}

final case class Response(
  status: Status,
  file: URI,
  hash: SHA256Hash,
  name: String,
  contentType: String,
  length: Long,
  remoteAddress: Option[InetAddress]
)

// DB types
sealed abstract class Operation(toString: String)
final case object Add extends Operation("Add")
final case object Remove extends Operation("Remove")

object Operation {
  implicit val operationColumnType: BaseColumnType[Operation] =
    MappedColumnType.base[Operation, String](
      { (op: Operation) => op.toString },
      { case "Add" => Add; case "Remove" => Remove }
    )
}

final case class File(
  hash: SHA256Hash,
  name: String,
  contentType: String,
  length: Long
)

final case class Event(
  tx: Long,
  timestamp: java.sql.Timestamp,
  operation: Operation,
  hash: SHA256Hash,
  remoteAddress: Option[InetAddress]
)
