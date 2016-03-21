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

package hyponome

import scala.sys.ShutdownHookThread

import hyponome.http._

object Main extends App {

  val sht: ShutdownHookThread = sys.addShutdownHook(shutdown)

  val service: HttpService = HttpService().start()

  private def shutdown(): Unit = { val stopped = service.stop(); () }
}
