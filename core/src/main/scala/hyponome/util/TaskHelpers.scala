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

package hyponome.util

import fs2.{Strategy, Task}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Future <-> Task conversion functions
  */
trait TaskHelpers {

  def futureToTask[A](fut: => Future[A])(implicit ec: ExecutionContext, s: Strategy): Task[A] =
    Task.fromFuture(fut)

  def taskToFuture[A](task: Task[A]): Future[A] =
    task.unsafeRunAsyncFuture
}
