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

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scalaz.Kleisli
import scalaz.concurrent.Task
import slick.driver.H2Driver.backend.DatabaseDef

package object hyponome extends Types {

  /** The LocalStore Monad */
  type LocalStoreM[A] = Kleisli[Task, LocalStoreContext, A]

  val LocalStoreM = new StoreM[LocalStoreContext]

  def localStore(implicit ec: ExecutionContext, store: Store[LocalStoreM, Path, DatabaseDef]): Store[LocalStoreM, Path, DatabaseDef] =
    store
}
