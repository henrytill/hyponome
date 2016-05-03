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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn
import scalaz.concurrent.{Task, TaskApp}
import hyponome.config._
import hyponome.db.HyponomeDB
import hyponome.file.LocalFileStore
import hyponome.test._
import hyponome.util._

object Main extends TaskApp {

  def server(cfg: ServiceConfig): Task[Unit] =
    for {
      db  <- Task.now(new HyponomeDB(cfg.db))
      _   <- futureToTask(db.init())
      st  <- Task.now(new LocalFileStore(cfg.store))
      _   <- st.init()
      svc <- Task.now(new Service(cfg, db, st))
      srv <- testServer(cfg, svc.root)
      _   <- Task.now(StdIn.readLine())
      _   <- srv.shutdown
    } yield ()

  override def runc: Task[Unit] = server(defaultConfig)
}
