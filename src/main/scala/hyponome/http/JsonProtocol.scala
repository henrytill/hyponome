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

package hyponome.http

import argonaut._
import Argonaut._
import java.net.{InetAddress, URI}
import java.nio.file.{FileSystems, Path}
import java.sql.Timestamp
import hyponome._
import hyponome.event._
import hyponome.query._

object JsonProtocol {

  implicit def sha256HashEncodeJson: EncodeJson[SHA256Hash] =
    jencode1L((h: SHA256Hash) => h.value)("SHA256Hash")

  implicit def sha256HashDecodeJson: DecodeJson[SHA256Hash] =
    jdecode1L(SHA256Hash.apply)("SHA256Hash")

  implicit def pathEncodeJson: EncodeJson[Path] =
    jencode1L((p: Path) => p.toFile.toString)("Path")

  implicit def pathDecodeJson: DecodeJson[Path] =
    jdecode1L(FileSystems.getDefault.getPath(_: String))("Path")

  implicit def inetAddressEncodeJson: EncodeJson[InetAddress] =
    jencode1L((i: InetAddress) => i.getHostAddress)("InetAddress")

  implicit def inetAddressDecodeJson: DecodeJson[InetAddress] =
    jdecode1L(InetAddress.getByName(_: String))("InetAddress")

  implicit def uriEncodeJson: EncodeJson[URI] =
    jencode1L((u: URI) => u.toString)("URI")

  implicit def uriDecodeJson: DecodeJson[URI] =
    jdecode1L(new URI(_: String))("URI")

  implicit def timestampEncodeJson: EncodeJson[Timestamp] =
    jencode1L((t: Timestamp) => t.toString)("Timestamp")

  implicit def timestampDecodeJson: DecodeJson[Timestamp] =
    jdecode1L(Timestamp.valueOf(_: String))("Timestamp")

  // https://gist.github.com/markhibberd/8231912
  def tagged[A](tag: String, c: HCursor, decoder: DecodeJson[A]): DecodeResult[A] =
    (c --\ tag).hcursor.fold(DecodeResult.fail[A]("Invalid tagged type", c.history))(decoder.decode)

  implicit def operationEncodeJson: EncodeJson[Operation] =
    EncodeJson(_ match {
      case AddToStore      => Json("AddToStore" := (()))
      case RemoveFromStore => Json("RemoveFromStore" := (()))
    })

  implicit def operationDecodeJson: DecodeJson[Operation] =
    DecodeJson(c =>
      tagged("AddToStore", c, implicitly[DecodeJson[Unit]].map(_ => AddToStore)) |||
        tagged("RemoveFromStore", c, implicitly[DecodeJson[Unit]].map(_ => RemoveFromStore)))

  implicit def addStatusEncodeJson: EncodeJson[AddStatus] =
    EncodeJson(_ match {
      case Created => Json("Created" := (()))
      case Exists  => Json("Exists" := (()))
    })

  implicit def addStatusDecodeJson: DecodeJson[AddStatus] =
    DecodeJson(c =>
      tagged("Created", c, implicitly[DecodeJson[Unit]].map(_ => Created)) |||
        tagged("Exists", c, implicitly[DecodeJson[Unit]].map(_ => Exists)))

  implicit def deleteStatusEncodeJson: EncodeJson[DeleteStatus] =
    EncodeJson(_ match {
      case Deleted  => Json("Deleted" -> jBool(true))
      case NotFound => Json("Deleted" -> jBool(false))
    })

  // TODO: deleteStatusDecodeJson

  implicit def AddCodecJson: CodecJson[Add] =
    casecodec8(Add.apply, Add.unapply)(
      "hostname",
      "port",
      "file",
      "hash",
      "name",
      "contentType",
      "length",
      "remoteAddress")

  implicit def AddedCodecJson: CodecJson[Added] =
    casecodec6(Added.apply, Added.unapply)(
      "status",
      "file",
      "hash",
      "name",
      "contentType",
      "length")

  implicit def StoreQueryResponseCodecJson: CodecJson[StoreQueryResponse] =
    casecodec8(StoreQueryResponse.apply, StoreQueryResponse.unapply)(
      "tx",
      "timestamp",
      "operation",
      "remoteAddress",
      "hash",
      "name",
      "contentType",
      "length")

  implicit def seqStoreQueryResponseEncodeJson[T]: EncodeJson[Seq[StoreQueryResponse]] =
    EncodeJson((ds: Seq[StoreQueryResponse]) =>
      jArray(ds.map(_.asJson).toList))
}
