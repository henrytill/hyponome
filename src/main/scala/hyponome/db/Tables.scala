/*
 * Copyright 2016 Henry Till
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hyponome.db

import hyponome.core._
import java.net.InetAddress
import java.sql.Timestamp
import slick.driver.H2Driver.api._

class Files(tag: Tag) extends Table[File](tag, "FILES") {
  def hash = column[SHA256Hash]("HASH", O.PrimaryKey, O.SqlType("CHARACTER(64)"))
  def name = column[Option[String]]("NAME")
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
