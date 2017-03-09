/*
 * Copyright 2016-2017 Henry Till
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

import hyponome.util._
import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.concurrent.Task

object LocalStoreM {

  def apply[A](f: LocalStoreContext => Task[A]): LocalStoreM[A] =
    Kleisli(f)

  def ask: LocalStoreM[LocalStoreContext] =
    Kleisli.ask[Task, LocalStoreContext]

  def fromCanThrow[A](x: => A): LocalStoreM[A] =
    apply[A]((_: LocalStoreContext) => Task[A](x))

  def fromTask[A](t: => Task[A]): LocalStoreM[A] =
    apply[A]((_: LocalStoreContext) => t)

  def fromFuture[A](x: => Future[A])(implicit ec: ExecutionContext): LocalStoreM[A] =
    apply[A]((_: LocalStoreContext) => futureToTask[A](x))

  def throwError[A](x: => Throwable): LocalStoreM[A] =
    apply[A]((_: LocalStoreContext) => Task.fail(x))
}
