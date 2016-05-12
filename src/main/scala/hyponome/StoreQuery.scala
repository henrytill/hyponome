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
import java.sql.Timestamp

sealed trait SortBy extends Product with Serializable
case object Tx extends SortBy
case object Time extends SortBy
case object Name extends SortBy
case object Address extends SortBy

sealed trait SortOrder extends Product with Serializable
case object Ascending extends SortOrder
case object Descending extends SortOrder

final case class StoreQuery(
  hash: Option[SHA256Hash] = None,
  name: Option[String] = None,
  remoteAddress: Option[InetAddress] = None,
  txLo: Option[Long] = None,
  txHi: Option[Long] = None,
  timeLo: Option[Timestamp] = None,
  timeHi: Option[Timestamp] = None,
  sortBy: SortBy = Tx,
  sortOrder: SortOrder = Ascending)
