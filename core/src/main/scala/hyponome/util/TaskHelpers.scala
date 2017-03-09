/*
 * Derived from:
 * https://github.com/http4s/http4s/blob/c84f5130d7aaeb0166d16b2c424e99733040925b/core/src/main/scala/org/http4s/util/Task.scala
 *
 * Copyright 2013-2014 http4s [http://www.http4s.org]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0]
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package hyponome.util

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}
import scalaz.concurrent.Task
import scalaz.{-\/, \/, \/-}

/**
  * Future <-> Task conversion functions
  */
trait TaskHelpers {

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  def futureToTask[A](fut: => Future[A])(implicit ec: ExecutionContext): Task[A] =
    Task.async { k =>
      fut.onComplete {
        case Success(a) => k(\/.right(a))
        case Failure(t) => k(\/.left(t))
      }
    }

  def taskToFuture[A](task: Task[A]): Future[A] = {
    val p: Promise[A] = Promise()
    task.unsafePerformAsync {
      case \/-(a) =>
        val _: Promise[A] = p.success(a)
      case -\/(t) =>
        val _: Promise[A] = p.failure(t)
    }
    p.future
  }
}
