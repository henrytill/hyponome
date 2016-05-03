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

package hyponome.core

import java.net.{InetAddress, URI}
import java.nio.file.Path
import java.sql.Timestamp
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.{BaseColumnType, MappedColumnType}

// DB Types

final case class SHA256Hash(value: String) {
  override def toString: String = value
}

object SHA256Hash {
  implicit val SHA256HashColumnType: BaseColumnType[SHA256Hash] =
    MappedColumnType.base[SHA256Hash, String](
      { case SHA256Hash(v: String) => v   },
      { case (v: String) => SHA256Hash(v) }
    )
}

sealed trait Operation extends Product with Serializable
case object Add extends Operation
case object Remove extends Operation

object Operation {
  implicit val operationColumnType: BaseColumnType[Operation] =
    MappedColumnType.base[Operation, String](
      { case Add => "Add"; case Remove => "Remove" },
      { case "Add" => Add; case "Remove" => Remove }
    )
}

final case class File(
  hash: SHA256Hash,
  name: Option[String],
  contentType: String,
  length: Long
)

final case class Event(
  tx: Long,
  timestamp: java.sql.Timestamp,
  operation: Operation,
  hash: SHA256Hash,
  remoteAddress: Option[InetAddress]
)

// POST
final case class Post(
  hostname: String,
  port: Int,
  file: Path,
  hash: SHA256Hash,
  name: Option[String],
  contentType: String,
  length: Long,
  remoteAddress: Option[InetAddress]
) {
  def mergeWithFile(f: File): Post = {
    Post(hostname, port, file, f.hash, f.name, f.contentType, f.length, remoteAddress)
  }
}

sealed trait PostStatus extends Product with Serializable
case object Created extends PostStatus
case object Exists extends PostStatus

final case class Posted(
  status: PostStatus,
  file: URI,
  hash: SHA256Hash,
  name: Option[String],
  contentType: String,
  length: Long
)

object Posted {
  def apply(post: Post, status: PostStatus): Posted = {
    val uri  = getURI(post.hostname, post.port, post.hash, post.name)
    Posted(status, uri, post.hash, post.name, post.contentType, post.length)
  }
}

// DELETE
final case class Delete(
  hash: SHA256Hash,
  remoteAddress: Option[InetAddress]
)

sealed trait DeleteStatus extends Product with Serializable
case object Deleted extends DeleteStatus
case object NotFound extends DeleteStatus

// GET File
final case class Result(file: Option[Path], name: Option[String])

final case class Redirect(uri: URI) {
  override def toString: String = uri.toString
}

// GET Query

sealed trait SortBy extends Product with Serializable
case object Tx extends SortBy
case object Time extends SortBy
case object Name extends SortBy
case object Address extends SortBy

sealed trait SortOrder extends Product with Serializable
case object Ascending extends SortOrder
case object Descending extends SortOrder

final case class DBQuery(
  hash: Option[SHA256Hash] = None,
  name: Option[String] = None,
  remoteAddress: Option[InetAddress] = None,
  txLo: Option[Long] = None,
  txHi: Option[Long] = None,
  timeLo: Option[Timestamp] = None,
  timeHi: Option[Timestamp] = None,
  sortBy: SortBy = Tx,
  sortOrder: SortOrder = Ascending
)

final case class DBQueryResponse(
  tx: Long,
  timestamp: java.sql.Timestamp,
  operation: Operation,
  remoteAddress: Option[InetAddress],
  hash: SHA256Hash,
  name: Option[String],
  contentType: String,
  length: Long
)
