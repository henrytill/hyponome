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

package hyponome.json

import hyponome._
import hyponome.test._
import io.circe.parser._
import io.circe.syntax._
import org.junit.{Assert, Test}

class JsonTests {

  @Test
  def serializeFileHash(): Unit = {
    val serialized = testPDFHash.asJson.noSpaces
    val expected   = "\"eba205fb9114750b2ce83db62f9c2a15dd068bcba31a2de32d8df7f7c8d85441\""
    Assert.assertEquals(expected, serialized)
    Assert.assertEquals(Right(testPDFHash), decode[FileHash](serialized))
  }

  @Test
  def serializeTestFile(): Unit = {
    val serialized = testFile.asJson.noSpaces
    val expected   = """|{"hash":"eba205fb9114750b2ce83db62f9c2a15dd068bcba31a2de32d8df7f7c8d85441",
                        |"name":"test.pdf",
                        |"contentType":"application/octet-stream",
                        |"length":24764,
                        |"metadata":{"data":"This is some metadata"}}""".stripMargin.replace("\n", "");
    Assert.assertEquals(expected, serialized)
    Assert.assertEquals(Right(testFile), decode[File](serialized))
  }
}
