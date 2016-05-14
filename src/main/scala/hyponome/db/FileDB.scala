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

import java.util.concurrent.atomic.AtomicLong
import hyponome._
import hyponome.event._
import hyponome.query._

trait FileDB[F[_]] {

  val tx: AtomicLong

  def init(): F[Unit]

  def close(): Unit

  def add(a: Add): F[AddStatus]

  def remove(r: Remove): F[RemoveStatus]

  def find(hash: SHA256Hash): F[Option[File]]

  def countFiles: F[Long]

  def runQuery(q: StoreQuery): F[Seq[StoreQueryResponse]]
}
