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

import org.http4s.HttpService
import org.http4s.server.SSLSupport.StoreInfo
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{Server, ServerApp, ServerBuilder, SSLSupport}
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.concurrent.Task
import hyponome.LocalStore
import hyponome.config._

object Main extends ServerApp {

  private val keypath: String = fs.getPath("src/main/resources/keystore.jks").toFile.toString

  private def builder: ServerBuilder with SSLSupport = BlazeBuilder

  private def serverTask(cfg: ServiceConfig, svc: HttpService): Task[Server] =
    builder.withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "password")
      .mountService(svc)
      .bindHttp(cfg.port)
      .start

  private def makeServer(cfg: ServiceConfig): Task[Server] =
    for {
      st  <- LocalStore(cfg)
      svc <- Task.now(new Service(cfg, st))
      srv <- serverTask(cfg, svc.root)
    } yield srv

  override def server(args: List[String]): Task[Server] = makeServer(defaultConfig)
}
