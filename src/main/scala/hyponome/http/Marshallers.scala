package hyponome.http

import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.model.ContentTypes._
import spray.json._

import hyponome.core._
import hyponome.http.JsonProtocol._

object Marshallers {

  implicit val responseM: ToResponseMarshaller[Response] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { (r: Response) =>
      val locationHeader = headers.Location(r.file.toString)
      HttpResponse(
        r.status.toStatusCode,
        headers = List(locationHeader),
        entity = HttpEntity(`text/plain(UTF-8)`, r.file.toString)
      )
    },
    Marshaller.withFixedContentType(`application/json`) { (r: Response) =>
      val locationHeader = headers.Location(r.file.toString)
      HttpResponse(
        r.status.toStatusCode,
        headers = List(locationHeader),
        entity = HttpEntity(`application/json`, r.toJson.prettyPrint)
      )
    }
  )

  implicit val serverErrorM: ToResponseMarshaller[(StatusCodes.ServerError, String)] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { case (sc, st) =>
      HttpResponse(sc, entity = HttpEntity(`text/plain(UTF-8)`, s"${sc.defaultMessage}  $st"))
    },
    Marshaller.withFixedContentType(`application/json`) { case (sc, st) =>
      HttpResponse(sc, entity = HttpEntity(`application/json`, JsObject("ERROR" -> JsString(st)).prettyPrint))
    }
  )
}
