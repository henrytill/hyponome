package hyponome.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import java.net.{InetAddress, URI}
import java.nio.file.{Files, FileSystem, FileSystems, Path}
import java.sql.Timestamp
import spray.json._

import hyponome.core._

object JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object sha256HashFormat extends RootJsonFormat[SHA256Hash] {
    def write(h: SHA256Hash) = JsObject("SHA256Hash" -> JsString(h.value))
    def read(value: JsValue) = value.asJsObject.getFields("SHA256Hash") match {
      case Seq(JsString(h)) => new SHA256Hash(h)
    }
  }

  implicit object pathJsonFormat extends RootJsonFormat[Path] {
    def write(p: Path) = JsObject("Path" -> JsString(p.toFile.toString))
    def read(value: JsValue) = value.asJsObject.getFields("Path") match {
      case Seq(JsString(p)) => FileSystems.getDefault().getPath(p)
    }
  }

  implicit object InetAddressFormat extends RootJsonFormat[InetAddress] {
    def write(i: InetAddress) = JsObject("InetAddress" -> JsString(i.getHostAddress))
    def read(value: JsValue) = value.asJsObject.getFields("InetAddress") match {
      case Seq(JsString(i)) => InetAddress.getByName(i)
    }
  }

  implicit object URIFormat extends RootJsonFormat[URI] {
    def write(uri: URI) = JsObject("URI" -> JsString(uri.toString))
    def read(value: JsValue) = value.asJsObject.getFields("URI") match {
      case Seq(JsString(uri)) => new URI(uri)
    }
  }

  implicit object TimestampFormat extends RootJsonFormat[Timestamp] {
    def write(t: Timestamp) = JsObject("Timestamp" -> JsString(t.toString))
    def read(value: JsValue) = value.asJsObject.getFields("Timestamp") match {
      case Seq(JsString(t)) => Timestamp.valueOf(t)
    }
  }

  implicit object OperationFormat extends RootJsonFormat[Operation] {
    def write(op: Operation) = op match {
      case Add    => JsString("Add")
      case Remove => JsString("Remove")
    }
    def read(value: JsValue) = (value: @unchecked) match {
      case JsString("Add")    => Add
      case JsString("Remove") => Remove
    }
  }

  implicit object PostStatusFormat extends RootJsonFormat[PostStatus] {
    def write(s: PostStatus) = s match {
      case Created => JsString("Created")
      case Exists  => JsString("Exists")
    }
    def read(value: JsValue) = (value: @unchecked) match {
      case JsString("Created") => Created
      case JsString("Exists")  => Exists
    }
  }

  implicit object DeleteStatusFormat extends RootJsonFormat[DeleteStatus] {
    def write(s: DeleteStatus) = s match {
      case Deleted   => JsObject("Deleted" -> JsBoolean(true))
      case NotFound  => JsObject("Deleted" -> JsBoolean(false))
    }
    def read(value: JsValue) = value.asJsObject.getFields("Deleted") match {
      case(Seq(JsBoolean(true)))  => Deleted
      case(Seq(JsBoolean(false))) => NotFound
    }
  }

  implicit val postFormat:            RootJsonFormat[Post]            = jsonFormat8(Post)
  implicit val postedFormat:          RootJsonFormat[Posted]          = jsonFormat6(Posted.apply)
  implicit val dbQueryResponseFormat: RootJsonFormat[DBQueryResponse] = jsonFormat8(DBQueryResponse)
}
