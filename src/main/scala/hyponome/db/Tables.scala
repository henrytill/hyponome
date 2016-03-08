package hyponome.db

import hyponome.core._
import java.net.InetAddress
import java.sql.Timestamp
import slick.driver.H2Driver.api._

@SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
class Files(tag: Tag) extends Table[File](tag, "FILES") {
  def hash = column[SHA256Hash]("HASH", O.PrimaryKey, O.SqlType("CHARACTER(64)"))
  def name = column[String]("NAME")
  def contentType = column[String]("CONTENT_TYPE")
  def length = column[Long]("LENGTH")
  def * = (hash, name, contentType, length) <> (File.tupled, File.unapply)
}

object Events {

  private def opInetAddressToString(ina: Option[InetAddress]): String =
    ina match {
      case Some(ina: InetAddress) => ina.getHostAddress
      case None                   => null
    }

  private def stringToOpInetAddress(s: String): Option[InetAddress] =
    s match {
      case x: String => Some(InetAddress.getByName(x))
      case null      => None
    }

  implicit val inetAddressColumnType: BaseColumnType[Option[InetAddress]] =
    MappedColumnType.base[Option[InetAddress], String](
      opInetAddressToString,
      stringToOpInetAddress
    )
}

@SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Nothing"))
class Events(tag: Tag) extends Table[Event](tag, "EVENTS") {
  import Events._
  def tx = column[Long]("TX", O.PrimaryKey)
  def timestamp = column[Timestamp]("TIMESTAMP", O.SqlType("TIMESTAMP AS CURRENT_TIMESTAMP"))
  def operation = column[Operation]("OPERATION")
  def hash = column[SHA256Hash]("HASH", O.SqlType("CHARACTER(64)"))
  def remoteAddress = column[Option[InetAddress]]("REMOTE_ADDRESS")
  def * = (tx, timestamp, operation, hash, remoteAddress) <> (Event.tupled, Event.unapply)
  def file = foreignKey("HASH_FK", hash, TableQuery[Files])(_.hash)
}
