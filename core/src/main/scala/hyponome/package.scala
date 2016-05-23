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

import java.net.URI
import java.nio.file.{Files, Path}
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import scalaz.concurrent.Task

package object hyponome {

  private def withInputStream[T](path: Path)(op: java.io.InputStream => T): T = {
    val fist = Files.newInputStream(path)
    try {
      op(fist)
    } finally fist.close()
  }

  def getSHA256Hash(p: Path): Task[SHA256Hash] = Task {
    val s: String = withInputStream(p)(sha256Hex)
    SHA256Hash(s)
  }

  def getURI(hostname: String, port: Int, hash: SHA256Hash, name: Option[String]): URI = {
    val end: String = name match {
      case Some(n) => s"$hash/$n"
      case None    => s"$hash"
    }
    new URI("https", s"//$hostname:$port/objects/$end", null)
  }
}
