/*
 * Copyright 2016-2017 Henry Till
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

package hyponome.db.tables

import hyponome._
import slick.jdbc.SQLiteProfile.api._

@SuppressWarnings(Array("org.wartremover.warts.Nothing"))
class Events(tag: Tag) extends Table[Event](tag, "EVENTS") {
  def id        = column[IdHash]("ID", O.PrimaryKey, O.SqlType("CHARACTER(64)"))
  def timestamp = column[Long]("TIMESTAMP")
  def operation = column[Operation]("OPERATION")
  def hash      = column[FileHash]("HASH", O.SqlType("CHARACTER(64)"))
  def user      = column[User]("USER")
  def message   = column[Option[Message]]("MESSAGE")
  def *         = (id, timestamp, operation, hash, user, message) <> (Event.tupled, Event.unapply)
  def file      = foreignKey("HASH_FK", hash, TableQuery[Files])(_.hash)
}
