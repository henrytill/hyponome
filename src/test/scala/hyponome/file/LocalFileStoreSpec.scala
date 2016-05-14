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

package hyponome.file

import java.nio.file.Path
import org.scalatest.{Matchers, WordSpecLike}
import hyponome._
import hyponome.event._
import hyponome.test._

class LocalFileStoreSpec extends WordSpecLike with Matchers {

  def withLocalFileStore(testCode: LocalFileStore => Any): Unit = {
    val t: LocalFileStore = new LocalFileStore(testStorePath)
    val _: Unit           = t.create().unsafePerformSync
    try { testCode(t); () }
    finally { deleteFolder(testStorePath); () }
  }

  "An instance of LocalFileStore" must {

    "have a getFileLocation method" which {
      "returns the correct file store Path" in withLocalFileStore { t =>
        val hash = testPDFHash
        val (dir: String, file: String) = hash.value.splitAt(2)
        val expected: Path = testStorePath.resolve(dir).resolve(file).toAbsolutePath
        t.getFileLocation(hash) should equal (expected)
      }
    }

    "have an existsInStore method" which {
      "completes with true if a file exists at the specified path in the file store" in withLocalFileStore { t =>
        t.add(add).flatMap { p =>
          val destination: Path = t.getFileLocation(testPDFHash)
          t.existsInStore(destination)
        }.unsafePerformSync should equal (true)
      }
      "completes with false if a file doesn't exist at the specified path in the file store" in withLocalFileStore { t =>
        val testHash: SHA256Hash = SHA256Hash("482bfece08d11246f41ce3dc43480e1b61659fbe0083b754bee09b44b940ae6c")
        t.existsInStore(t.getFileLocation(testHash)).unsafePerformSync should equal (false)
      }
    }

    "have an add method" which {
      "copies a file to the correct file store Path" in withLocalFileStore { t =>
        t.add(add).flatMap { p =>
          val destination: Path = t.getFileLocation(testPDFHash)
          getSHA256Hash(destination)
        }.unsafePerformSync should equal (testPDFHash)
      }
      "completes with Added after successfully copying a file to the file store" in withLocalFileStore { t =>
        t.add(add).unsafePerformSync should equal (Added)
      }
      "completes with Exists when trying to copy a file to a path where one already exists" in withLocalFileStore { t =>
        t.add(add).flatMap { _ =>
          t.add(add)
        }.unsafePerformSync should equal (Exists)
      }
    }

    "have a remove method" which {
      "removes a file with the specified hash from the file store" in withLocalFileStore { t =>
        t.add(add).flatMap { _ =>
          t.remove(testPDFHash)
        }.flatMap { _ =>
          t.existsInStore(t.getFileLocation(testPDFHash))
        }.unsafePerformSync should equal (false)
      }
      "completes with Removed after successfully deleting a file from the file store" in withLocalFileStore { t =>
        t.add(add).flatMap { _ =>
          t.remove(testPDFHash)
        }.unsafePerformSync should equal (Removed)
      }
      "completes with NotFound when attempting to remove a file which doesn't exist in the file store" in withLocalFileStore { t =>
        t.remove(testPDFHash).unsafePerformSync should equal (NotFound)
      }
    }
  }
}
