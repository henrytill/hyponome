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
        entity = HttpEntity(`text/plain(UTF-8)`, r.toJson.prettyPrint)
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

  implicit val infoM: ToResponseMarshaller[Info] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { (i: Info) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(`text/plain(UTF-8)`, i.toJson.prettyPrint)
      )
    },
    Marshaller.withFixedContentType(`application/json`) { (i: Info) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(`application/json`, i.toJson.prettyPrint)
      )
    }
  )

  implicit val okM: ToResponseMarshaller[OK] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`text/plain(UTF-8)`) { (ok: OK) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(`text/plain(UTF-8)`, ok.toJson.prettyPrint)
      )
    },
    Marshaller.withFixedContentType(`application/json`) { (ok: OK) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(`application/json`, ok.toJson.prettyPrint)
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
