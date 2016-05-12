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

import slick.driver.H2Driver.api._
import slick.driver.H2Driver.{BaseColumnType, MappedColumnType}

sealed trait Operation extends Product with Serializable
case object AddToStore extends Operation
case object RemoveFromStore extends Operation

object Operation {
  implicit val operationColumnType: BaseColumnType[Operation] =
    MappedColumnType.base[Operation, String](
      { case AddToStore      => "AddToStore";
        case RemoveFromStore => "RemoveFromStore" },
      { case "AddToStore"      => AddToStore
        case "RemoveFromStore" => RemoveFromStore })
}
