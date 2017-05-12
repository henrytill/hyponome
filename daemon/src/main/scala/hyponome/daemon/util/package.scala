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

package hyponome.daemon

import java.net.ServerSocket
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{ExecutorService, TimeUnit}

import cats.syntax.either._
import hyponome.util._

package object util {

  def intToByteArray(i: Int): Array[Byte] =
    ByteBuffer
      .allocate(8)
      .order(ByteOrder.BIG_ENDIAN)
      .putInt(i)
      .array()

  @SuppressWarnings(Array("org.wartremover.warts.Equals", "org.wartremover.warts.Throw", "org.wartremover.warts.Nothing"))
  def byteArrayToInt(bytes: Array[Byte]): Either[Throwable, Int] =
    if (bytes.length != 8)
      Left(new IllegalArgumentException(s"byteArrayToInt: expected length of 8, but length was ${bytes.length}"))
    else
      Either.catchNonFatal {
        ByteBuffer
          .wrap(bytes)
          .order(ByteOrder.BIG_ENDIAN)
          .getInt()
      }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def shutdownAndAwaitTermination(pool: ExecutorService): Unit = {
    pool.shutdown()
    try {
      if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
        ignore { pool.shutdownNow() }
        if (!pool.awaitTermination(60, TimeUnit.SECONDS))
          System.err.println("Pool did not terminate")
      }
    } catch {
      case e: InterruptedException =>
        ignore { pool.shutdownNow() }
        Thread.currentThread().interrupt();
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def shutdownAndAwaitTermination(asg: AsynchronousChannelGroup): Unit = {
    asg.shutdown()
    try {
      if (!asg.awaitTermination(60, TimeUnit.SECONDS)) {
        ignore { asg.shutdownNow() }
        if (!asg.awaitTermination(60, TimeUnit.SECONDS))
          System.err.println("AsynchronousChannelGroup did not terminate");
      }
    } catch {
      case e: Throwable =>
        ignore { asg.shutdownNow() }
    }
  }

  def getOpenPort(): Int = {
    val socket = new ServerSocket(0)
    try {
      val port = socket.getLocalPort
      socket.setReuseAddress(true)
      socket.close()
      port
    } catch {
      case _: Throwable =>
        getOpenPort()
    }
  }
}
