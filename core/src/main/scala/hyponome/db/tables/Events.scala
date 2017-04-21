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
import slick.lifted.{ForeignKeyQuery, ProvenShape}

@SuppressWarnings(Array("org.wartremover.warts.Nothing"))
class Events(tag: Tag) extends Table[Event](tag, "EVENTS") {
  def id: Rep[hyponome.IdHash]                    = column[IdHash]("ID", O.PrimaryKey, O.SqlType("CHARACTER(64)"))
  def timestamp: Rep[Long]                        = column[Long]("TIMESTAMP")
  def operation: Rep[hyponome.Operation]          = column[Operation]("OPERATION")
  def hash: Rep[hyponome.FileHash]                = column[FileHash]("HASH", O.SqlType("CHARACTER(64)"))
  def user: Rep[hyponome.User]                    = column[User]("USER")
  def message: Rep[Option[hyponome.Message]]      = column[Option[Message]]("MESSAGE")
  def * : ProvenShape[hyponome.Event]             = (id, timestamp, operation, hash, user, message) <> (Event.tupled, Event.unapply)
  def file: ForeignKeyQuery[Files, hyponome.File] = foreignKey("HASH_FK", hash, TableQuery[Files])(_.hash)
}
