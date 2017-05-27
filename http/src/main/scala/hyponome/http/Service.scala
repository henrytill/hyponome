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

package hyponome.http

import java.io.{File => JFile}

import fs2.interop.cats._
import hyponome.{NotFound => _, _}
import org.http4s._
import org.http4s.dsl._

@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class Service(store: Store[LocalStore.T, Path], ctx: LocalStoreContext) {

  def retrieveFile(req: Request, h: FileHash): LocalStore.T[Response] =
    store.findFile(h).map { (maybeFile: Option[JFile]) =>
      maybeFile
        .flatMap((file: JFile) => StaticFile.fromFile(file, Some(req)))
        .getOrElse(Response(Status.NotFound))
    }

  def getFile(req: Request, h: FileHash): LocalStore.T[Response] =
    store.info(h).flatMap { (maybeInfo: Option[File]) =>
      maybeInfo match {
        case None => LocalStore.fromTask(NotFound())
        case Some(f) =>
          f.name match {
            case None       => retrieveFile(req, h)
            case Some(name) => LocalStore.fromTask(PermanentRedirect(Uri(path = s"/objects/$h/$name")))
          }
      }
    }

  def getFile(req: Request, h: FileHash, name: String): LocalStore.T[Response] =
    store.info(h).flatMap {
      case Some(f) if f.name.getOrElse("") == name => retrieveFile(req, h)
      case _                                       => LocalStore.fromTask(NotFound())
    }

  val root = HttpService {

    case req @ GET -> Root / "objects" / hash =>
      getFile(req, FileHash.fromHex(hash)).run(ctx)

    case req @ GET -> Root / "objects" / hash / filename =>
      getFile(req, FileHash.fromHex(hash), filename).run(ctx)
  }
}
