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

package hyponome.file

import scalaz.concurrent.Task
import hyponome.{Add, SHA256Hash}
import hyponome.event._

trait FileStore[A] {

  def exists(): Task[Boolean]

  def create(): Task[Unit]

  def getFileLocation(hash: SHA256Hash): A

  def existsInStore(p: A): Task[Boolean]

  def copyToStore(a: Add): Task[AddStatus]

  def deleteFromStore(hash: SHA256Hash): Task[DeleteStatus]
}
