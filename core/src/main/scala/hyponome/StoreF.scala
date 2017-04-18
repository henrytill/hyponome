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

import cats.data.Kleisli
import fs2.{Strategy, Task}
import fs2.interop.cats._
import hyponome.util._
import scala.concurrent.{ExecutionContext, Future}

final class StoreF[Context] {

  type T[A] = Kleisli[Task, Context, A]

  def pure[A](a: A): T[A] =
    Kleisli.pure(a)

  def apply[A](f: Context => Task[A]): T[A] =
    Kleisli(f)

  def ask: Kleisli[Task, Context, Context] =
    Kleisli.ask[Task, Context]

  def fromCanThrow[A](x: => A)(implicit s: Strategy): T[A] =
    apply[A]((_: Context) => Task[A](x))

  def fromTask[A](t: => Task[A]): T[A] =
    apply[A]((_: Context) => t)

  def fromFuture[A](x: => Future[A])(implicit ec: ExecutionContext, s: Strategy): T[A] =
    apply[A]((_: Context) => futureToTask(x))

  def throwError[A](x: => Throwable): T[A] =
    apply[A]((_: Context) => Task.fail(x))
}
