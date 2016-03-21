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

import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.model.ContentTypes._
import spray.json._

import hyponome.core._
import hyponome.http.JsonProtocol._

object Marshallers {

  implicit val responseM: ToResponseMarshaller[Posted] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { (p: Posted) =>
      val locationHeader = headers.Location(p.file.toString)
      HttpResponse(
        p.status.toStatusCode,
        headers = List(locationHeader),
        entity = HttpEntity(`text/plain(UTF-8)`, p.toJson.prettyPrint)
      )
    },
    Marshaller.withFixedContentType(`application/json`) { (p: Posted) =>
      val locationHeader = headers.Location(p.file.toString)
      HttpResponse(
        p.status.toStatusCode,
        headers = List(locationHeader),
        entity = HttpEntity(`application/json`, p.toJson.prettyPrint)
      )
    }
  )

  implicit val redirectM: ToResponseMarshaller[Redirect] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { (r: Redirect) =>
      val locationHeader = headers.Location(r.toString)
      HttpResponse(
        StatusCodes.Found,
        headers = List(locationHeader),
        entity = HttpEntity(`text/plain(UTF-8)`, r.uri.toJson.prettyPrint)
      )
    },
    Marshaller.withFixedContentType(`application/json`) { (r: Redirect) =>
      val locationHeader = headers.Location(r.toString)
      HttpResponse(
        StatusCodes.Found,
        headers = List(locationHeader),
        entity = HttpEntity(`application/json`, r.uri.toJson.prettyPrint)
      )
    }
  )

  implicit val deletedM: ToResponseMarshaller[DeleteStatus] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { (d: DeleteStatus) =>
      HttpResponse(
        d.toStatusCode,
        entity = HttpEntity(`text/plain(UTF-8)`, d.toJson.prettyPrint)
      )
    },
    Marshaller.withFixedContentType(`application/json`) { (d: DeleteStatus) =>
      HttpResponse(
        d.toStatusCode,
        entity = HttpEntity(`application/json`, d.toJson.prettyPrint)
      )
    }
  )

  implicit val seqDBQueryResponseM: ToResponseMarshaller[Seq[DBQueryResponse]] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { (rs: Seq[DBQueryResponse]) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(`text/plain(UTF-8)`, rs.toJson.prettyPrint)
      )
    },
    Marshaller.withFixedContentType(`application/json`) { (rs: Seq[DBQueryResponse]) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(`application/json`, rs.toJson.prettyPrint)
      )
    }
  )

  implicit val serverErrorM: ToResponseMarshaller[(StatusCodes.ServerError, String)] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { case (sc, st) =>
      HttpResponse(sc, entity = HttpEntity(`text/plain(UTF-8)`, JsObject("error" -> JsString(st)).prettyPrint))
    },
    Marshaller.withFixedContentType(`application/json`) { case (sc, st) =>
      HttpResponse(sc, entity = HttpEntity(`application/json`, JsObject("error" -> JsString(st)).prettyPrint))
    }
  )
}
