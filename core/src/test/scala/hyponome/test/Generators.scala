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

package hyponome.test

import org.scalacheck.{Arbitrary, Gen}

trait Generators {

  val genNEByteArray: Gen[Array[Byte]] = Gen.nonEmptyContainerOf[Array, Byte](Arbitrary.arbitrary[Byte])

  val genNEListOfNEByteArrays: Gen[List[Array[Byte]]] = Gen.nonEmptyContainerOf[List, Array[Byte]](genNEByteArray)

  def sizedByteArray(size: Int): Gen[Array[Byte]] = Gen.listOfN(size, Arbitrary.arbitrary[Byte]).map(_.toArray)

  def sizedListOfSizedByteArrays(listSize: Int, byteArraySize: Int): Gen[List[Array[Byte]]] =
    Gen.listOfN(listSize, sizedByteArray(byteArraySize))
}
