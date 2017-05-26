/*
 * Copyright 2017 Henry Till
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

import cats.syntax.either._
import io.circe._
import io.circe.generic.extras.semiauto._

package object json {

  import hyponome.json.auto._

  /* Decode and encode */
  implicit val operationDecoder: Decoder[Operation] = deriveDecoder[Operation]
  implicit val operationEncoder: Encoder[Operation] = deriveEncoder[Operation]

  implicit val idHashDecoder: Decoder[IdHash] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(IdHash.fromHex(str)).leftMap(t => "IdHash")
    }
  implicit val idHashEncoder: Encoder[IdHash] =
    Encoder.encodeString.contramap[IdHash](_.toString)

  implicit val fileHashDecoder: Decoder[FileHash] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(FileHash.fromHex(str)).leftMap(t => "FileHash")
    }
  implicit val fileHashEncoder: Encoder[FileHash] =
    Encoder.encodeString.contramap[FileHash](_.toString)

  implicit val metadataDecoder: Decoder[Metadata] = deriveDecoder[Metadata]
  implicit val metadataEncoder: Encoder[Metadata] = deriveEncoder[Metadata]

  implicit val userDecoder: Decoder[User] = deriveDecoder[User]
  implicit val userEncoder: Encoder[User] = deriveEncoder[User]

  implicit val messageDecoder: Decoder[Message] = deriveDecoder[Message]
  implicit val messageEncoder: Encoder[Message] = deriveEncoder[Message]

  implicit val fileDecoder: Decoder[File] = deriveDecoder[File]
  implicit val fileEncoder: Encoder[File] = deriveEncoder[File]

  implicit val eventDecoder: Decoder[Event] = deriveDecoder[Event]
  implicit val eventEncoder: Encoder[Event] = deriveEncoder[Event]

  implicit val sortByDecoder: Decoder[SortBy] = deriveDecoder[SortBy]
  implicit val sortByEncoder: Encoder[SortBy] = deriveEncoder[SortBy]

  implicit val sortOrderDecoder: Decoder[SortOrder] = deriveDecoder[SortOrder]
  implicit val sortOrderEncoder: Encoder[SortOrder] = deriveEncoder[SortOrder]

  implicit val storeQueryDecoder: Decoder[StoreQuery] = deriveDecoder[StoreQuery]
  implicit val storeQueryEncoder: Encoder[StoreQuery] = deriveEncoder[StoreQuery]

  /* Encode-only */
  implicit val defaultEncoder: Encoder[Default] = deriveEncoder[Default]
  implicit val appErrorEncoder: Encoder[AppError] =
    new Encoder[AppError] {
      final def apply(ae: AppError): Json = ae match {
        case d: Default => Encoder[Default].apply(d)
      }
    }

  implicit val storeStatusEncoder: Encoder[StoreStatus]         = deriveEncoder[StoreStatus]
  implicit val dbStatusEncoder: Encoder[DBStatus]               = deriveEncoder[DBStatus]
  implicit val fileStoreStatusEncoder: Encoder[FileStoreStatus] = deriveEncoder[FileStoreStatus]

  implicit val addedEncoder: Encoder[Added]   = deriveEncoder[Added]
  implicit val existsEncoder: Encoder[Exists] = deriveEncoder[Exists]
  implicit val addStatusEncoder: Encoder[AddStatus] =
    new Encoder[AddStatus] {
      final def apply(as: AddStatus): Json = as match {
        case a: Added  => Encoder[Added].apply(a)
        case e: Exists => Encoder[Exists].apply(e)
      }
    }

  implicit val removedEncoder: Encoder[Removed]   = deriveEncoder[Removed]
  implicit val notFoundEncoder: Encoder[NotFound] = deriveEncoder[NotFound]
  implicit val removeStatusEncoder: Encoder[RemoveStatus] =
    new Encoder[RemoveStatus] {
      final def apply(rs: RemoveStatus): Json = rs match {
        case r: Removed  => Encoder[Removed].apply(r)
        case n: NotFound => Encoder[NotFound].apply(n)
      }
    }

  implicit val storeQueryResponseEncoder: Encoder[StoreQueryResponse] = deriveEncoder[StoreQueryResponse]
}
