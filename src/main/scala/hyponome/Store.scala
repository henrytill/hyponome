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

package hyponome

import java.io.{File => JFile}
import scalaz.concurrent.Task
import hyponome.db._
import hyponome.event._
import hyponome.file._
import hyponome.query._

trait Store[F] {

  val fileStore: FileStore[F]

  val db: HyponomeDB

  def info(h: SHA256Hash): Task[Option[File]]

  def count: Task[Long]

  def query(q: StoreQuery): Task[Seq[StoreQueryResponse]]

  def put(a: Add): Task[Added]

  def exists(h: SHA256Hash): Task[Boolean]

  def get(h: SHA256Hash): Task[Option[JFile]]

  def delete(d: Delete): Task[DeleteStatus]
}
