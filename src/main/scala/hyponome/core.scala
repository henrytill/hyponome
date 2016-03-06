package hyponome.core

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import java.net.{InetAddress, URI}
import java.nio.file.Path
import java.sql.Timestamp
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

final case class OK(ok: Boolean)

final case object Objects
final case class Info(path: String, count: Long, max: Long)

// DB types
sealed trait Operation extends Product with Serializable
final case object Add extends Operation
final case object Remove extends Operation

object Operation {
  implicit val operationColumnType: BaseColumnType[Operation] =
    MappedColumnType.base[Operation, String](
      { case Add => "Add"; case Remove => "Remove" },
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

// Query

sealed trait SortBy extends Product with Serializable
final case object Tx extends SortBy
final case object Time extends SortBy
final case object Address extends SortBy

sealed trait SortOrder extends Product with Serializable
final case object Ascending extends SortOrder
final case object Descending extends SortOrder

@SuppressWarnings(Array("org.brianmckenna.wartremover.warts.DefaultArguments"))
final case class DBQuery(
  hash: Option[SHA256Hash] = None,
  remoteAddress: Option[InetAddress] = None,
  txLo: Option[Long] = None,
  txHi: Option[Long] = None,
  timeLo: Option[Timestamp] = None,
  timeHi: Option[Timestamp] = None,
  sortBy: SortBy = Tx,
  sortOrder: SortOrder = Ascending
)

final case class DBQueryResponse(
  tx: Long,
  timestamp: java.sql.Timestamp,
  operation: Operation,
  remoteAddress: Option[InetAddress],
  hash: SHA256Hash,
  name: String,
  contentType: String,
  length: Long
)
