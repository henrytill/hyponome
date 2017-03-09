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

import java.nio.ByteBuffer
import java.nio.file.{Files, Path}

package object util extends TaskHelpers {

  implicit class LongHelpers(val self: Long) extends AnyVal {
    def bytes: Array[Byte] =
      ByteBuffer.allocate(java.lang.Long.SIZE / java.lang.Byte.SIZE).putLong(Long.box(self)).array
  }

  def withInputStream[T](path: Path)(op: java.io.InputStream => T): T = {
    val fist = Files.newInputStream(path)
    try op(fist)
    finally fist.close()
  }
}
