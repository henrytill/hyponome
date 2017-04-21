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
import slick.lifted.ProvenShape

@SuppressWarnings(Array("org.wartremover.warts.Nothing"))
class Files(tag: Tag) extends Table[File](tag, "FILES") {
  def hash: Rep[hyponome.FileHash]             = column[FileHash]("HASH", O.PrimaryKey, O.SqlType("CHARACTER(64)"))
  def name: Rep[Option[String]]                = column[Option[String]]("NAME")
  def contentType: Rep[Option[String]]         = column[Option[String]]("CONTENT_TYPE")
  def length: Rep[Long]                        = column[Long]("LENGTH")
  def metadata: Rep[Option[hyponome.Metadata]] = column[Option[Metadata]]("METADATA")
  def * : ProvenShape[hyponome.File]           = (hash, name, contentType, length, metadata) <> (File.tupled, File.unapply)
}
