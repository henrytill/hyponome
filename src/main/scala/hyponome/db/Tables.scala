package hyponome

import java.sql.Timestamp
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.{BaseColumnType, MappedColumnType}

final case class SHA256Hash(value: String) {
  override def toString: String = value
}

object SHA256Hash {
  implicit val SHA256HashColumnType: BaseColumnType[SHA256Hash] = MappedColumnType.base[SHA256Hash, String](
    { case SHA256Hash(v: String) => v   },
    { case (v: String) => SHA256Hash(v) }
  )
}

sealed trait Operation
case object Add extends Operation
case object Remove extends Operation

object Operation {
  implicit val operationColumnType: BaseColumnType[Operation] = MappedColumnType.base[Operation, Int](
    { case Add => 1; case Remove => -1 },
    { case 1 => Add; case -1 => Remove }
  )
}

final case class File(
  hash: SHA256Hash,
  filename: String,
  contentType: String,
  length: Long
)

final case class Event(
  id: Long,
  timestamp: Timestamp,
  operation: Operation,
  hash: SHA256Hash,
  remoteAddress: String
)

@SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
class Files(tag: Tag) extends Table[File](tag, "FILES") {
  def hash = column[SHA256Hash]("HASH", O.PrimaryKey, O.SqlType("CHARACTER(64)"))
  def filename = column[String]("FILENAME")
  def contentType = column[String]("CONTENT_TYPE")
  def length = column[Long]("LENGTH")
  def * = (hash, filename, contentType, length) <> (File.tupled, File.unapply)
}

@SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
class Events(tag: Tag) extends Table[Event](tag, "EVENTS") {
  def tx = column[Long]("TX", O.AutoInc)
  def timestamp = column[Timestamp]("TIMESTAMP", O.SqlType("TIMESTAMP AS CURRENT_TIMESTAMP"))
  def operation = column[Operation]("OPERATION")
  def hash = column[SHA256Hash]("HASH", O.SqlType("CHARACTER(64)"))
  def remoteAddress = column[String]("REMOTE_ADDRESS")
  def * = (tx, timestamp, operation, hash, remoteAddress) <> (Event.tupled, Event.unapply)
  def file = foreignKey("HASH_FK", hash, TableQuery[Files])(_.hash)
}

/**
@SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
class Objects(tag: Tag) extends Table[(Long, Timestamp, Operation, String, String, String, Long, String)](tag, "OBJECTS") {
  def id = column[Long]("TX", O.SqlType("BIGINT UNIQUE"))
  def timestamp = column[Timestamp]("TIMESTAMP", O.SqlType("TIMESTAMP AS CURRENT_TIMESTAMP"))
  def operation = column[Operation]("OPERATION")
  def hash = column[String]("HASH", O.PrimaryKey, O.SqlType("CHARACTER(64)"))
  def filename = column[String]("FILENAME")
  def contentType = column[String]("CONTENT_TYPE")
  def length = column[Long]("LENGTH")
  def remoteAddress = column[String]("REMOTE_ADDRESS")
  def * = (id, timestamp, operation, hash, filename, contentType, length, remoteAddress)
}
  */
