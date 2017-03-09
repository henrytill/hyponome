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

import java.nio.file.{Files, Path}
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.scalacheck.{Arbitrary, Gen, Prop}
import scalaz.concurrent.Task

class FileStoreTest {

  val genNEByteArray: Gen[Array[Byte]] = Gen.nonEmptyContainerOf[Array, Byte](Arbitrary.arbitrary[Byte])

  val genNEListOfNEByteArrays: Gen[List[Array[Byte]]] = Gen.nonEmptyContainerOf[List, Array[Byte]](genNEByteArray)

  def roundTrip(ps: List[Path]): Task[List[FileHash]] = ???

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  val prop_roundTrip: Prop = Prop.forAll(genNEListOfNEByteArrays) { (bas: List[Array[Byte]]) =>
    (for {
      t  <- Task.now(Files.createTempDirectory("hyponome-test-uploads-"))
      hs <- Task.now(bas.map(sha256Hex))
      ps <- Task.now(hs.map(t.resolve))
      zs <- Task.now(bas.zip(ps))
      _  <- Task.now(zs.foreach((t: (Array[Byte], Path)) => Files.write(t._2, t._1)))
      r  <- roundTrip(ps)
    } yield r == hs.map(FileHash.fromHex(_))).unsafePerformSync
  }
}
