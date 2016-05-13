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

package hyponome.query

import java.net.InetAddress
import java.sql.Timestamp
import hyponome._
import hyponome.event._

final case class StoreQueryResponse(
  tx: Long,
  timestamp: Timestamp,
  operation: Operation,
  remoteAddress: Option[InetAddress],
  hash: SHA256Hash,
  name: Option[String],
  contentType: String,
  length: Long)
