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

import java.net.InetAddress
import java.nio.file.Path

final case class Add(
  hostname: String,
  port: Int,
  file: Path,
  hash: SHA256Hash,
  name: Option[String],
  contentType: String,
  length: Long,
  remoteAddress: Option[InetAddress]) {

  def mergeWithFile(f: File): Add = {
    Add(hostname, port, file, f.hash, f.name, f.contentType, f.length, remoteAddress)
  }
}
