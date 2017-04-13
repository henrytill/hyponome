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

package hyponome

import hyponome.util._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import javax.xml.bind.DatatypeConverter.parseHexBinary
import net.xngns.klados.hash.SHA256Hash
import scalaz.concurrent.Task
import slick.driver.SQLiteDriver.api._
import slick.driver.SQLiteDriver.{BaseColumnType, MappedColumnType}

trait Types {

  abstract class AppError(msg: String) extends Exception(msg)
  case class Default(msg: String)      extends AppError(msg)

  sealed trait StoreStatus extends Product with Serializable
  case object StoreCreated extends StoreStatus
  case object StoreExists  extends StoreStatus

  sealed trait DBStatus     extends Product with Serializable
  case object DBInitialized extends DBStatus
  case object DBExists      extends DBStatus

  sealed trait FileStoreStatus extends Product with Serializable
  case object FileStoreCreated extends FileStoreStatus
  case object FileStoreExists  extends FileStoreStatus

  case class DBSchemaVersion(version: Int) {
    def toFile(p: Path): Task[Unit] =
      Task
        .now(Files.write(p, version.toString.getBytes(StandardCharsets.UTF_8)))
        .map((_: Path) => ())
  }

  object DBSchemaVersion {
    def fromFile(p: Path): Task[DBSchemaVersion] =
      Task
        .now(Files.readAllBytes(p))
        .map((bytes: Array[Byte]) => new String(bytes, StandardCharsets.UTF_8))
        .map((s: String) => Integer.parseInt(s))
        .map((i: Int) => DBSchemaVersion(i))
  }

  trait AddStatus extends Product with Serializable {
    val hash: FileHash
  }
  case class Added(override val hash: FileHash)  extends AddStatus
  case class Exists(override val hash: FileHash) extends AddStatus

  trait RemoveStatus extends Product with Serializable {
    val hash: FileHash
  }
  case class Removed(override val hash: FileHash)  extends RemoveStatus
  case class NotFound(override val hash: FileHash) extends RemoveStatus

  sealed trait Operation      extends Product with Serializable
  case object AddToStore      extends Operation
  case object RemoveFromStore extends Operation

  object Operation {
    implicit val operationColumnType: BaseColumnType[Operation] =
      MappedColumnType.base[Operation, String]({
        case AddToStore      => "AddToStore";
        case RemoveFromStore => "RemoveFromStore"
      }, {
        case "AddToStore"      => AddToStore
        case "RemoveFromStore" => RemoveFromStore
      })
  }

  class IdHash(bytes: Array[Byte]) extends SHA256Hash(bytes)

  object IdHash {
    def apply(bytes: Array[Byte]): IdHash =
      new IdHash(bytes)

    def fromBytes(bs: Array[Byte]): IdHash =
      IdHash(SHA256Hash(bs).getBytes)

    def fromHex(s: String): IdHash =
      IdHash(parseHexBinary(s))

    implicit val IdHashColumnType: BaseColumnType[IdHash] =
      MappedColumnType.base[IdHash, String](_.toString, IdHash.fromHex)
  }

  class FileHash(bytes: Array[Byte]) extends SHA256Hash(bytes)

  object FileHash {
    def apply(bytes: Array[Byte]): FileHash =
      new FileHash(bytes)

    def fromBytes(bs: Array[Byte]): FileHash =
      FileHash(SHA256Hash(bs).getBytes)

    def fromPath(p: Path): Task[FileHash] = Task {
      val hash: SHA256Hash = withInputStream(p)(SHA256Hash.apply)
      FileHash(hash.getBytes)
    }

    def fromHex(s: String): FileHash =
      FileHash(parseHexBinary(s))

    implicit val FileHashColumnType: BaseColumnType[FileHash] =
      MappedColumnType.base[FileHash, String](_.toString, FileHash.fromHex)
  }

  case class Metadata(data: String)

  object Metadata {
    def empty: Metadata = Metadata("")

    implicit val MetadataColumnType: BaseColumnType[Metadata] =
      MappedColumnType.base[Metadata, String](_.data, Metadata.apply)
  }

  case class User(name: String, email: String) {
    override def toString: String = s"$name <$email>"
  }

  object User {
    def fromString(s: String): User = {
      val user = """(.*)<(\S*)>""".r
      s match {
        case user(name, email) => User(name.trim, email)
      }
    }

    implicit val UserColumnType: BaseColumnType[User] =
      MappedColumnType.base[User, String](_.toString, User.fromString)
  }

  case class Message(msg: String)

  object Message {
    implicit val MessageColumnType: BaseColumnType[Message] =
      MappedColumnType.base[Message, String](_.msg, Message.apply)
  }

  case class File(hash: FileHash, name: Option[String], contentType: Option[String], length: Long, metadata: Option[Metadata])

  case class Event(id: IdHash, timestamp: Long, operation: Operation, file: FileHash, user: User, message: Option[Message])

  sealed trait SortBy    extends Product with Serializable
  case object SortByTime extends SortBy
  case object SortByName extends SortBy
  case object SortByUser extends SortBy

  sealed trait SortOrder extends Product with Serializable
  case object Ascending  extends SortOrder
  case object Descending extends SortOrder

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  case class StoreQuery(hash: Option[FileHash] = None,
                        name: Option[String] = None,
                        user: Option[User] = None,
                        txLo: Option[Long] = None,
                        txHi: Option[Long] = None,
                        timeLo: Option[Long] = None,
                        timeHi: Option[Long] = None,
                        sortBy: SortBy = SortByTime,
                        sortOrder: SortOrder = Ascending)

  case class StoreQueryResponse(id: IdHash,
                                timestamp: Long,
                                operation: Operation,
                                user: User,
                                hash: FileHash,
                                name: Option[String],
                                contentType: Option[String],
                                length: Long,
                                metadata: Option[Metadata])
}
