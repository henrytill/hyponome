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

package hyponome.protobuf

import java.io.{FileInputStream, FileOutputStream}
import java.nio.file.Path
import java.security.MessageDigest

import com.google.protobuf.ByteString
import fs2.Strategy
import hyponome._
import hyponome.test._
import org.junit.{Assert, Test}

import scala.collection.JavaConverters._

@SuppressWarnings(
  Array("org.wartremover.warts.Equals",
        "org.wartremover.warts.DefaultArguments",
        "org.wartremover.warts.NonUnitStatements",
        "org.wartremover.warts.ToString"))
class FileProtoTest {

  implicit val S: Strategy = Strategy.fromFixedDaemonPool(8, threadName = "worker")

  def splitFile(path: Path, chunkSize: Int = 512): (FileProto.File, Array[FileChunkProto.FileChunk]) = {
    val fis: FileInputStream = new FileInputStream(path.toFile)
    try {
      val name: String        = path.getFileName.toString
      val fileLength: Long    = path.toFile.length()
      val numberOfChunks: Int = math.ceil(fileLength.toDouble / chunkSize.toDouble).toInt
      val lastChunkSize: Int  = (fileLength % chunkSize.toLong).toInt
      val chunkSizes =
        if (lastChunkSize == 0) {
          (0 until numberOfChunks).map(_ => chunkSize)
        } else {
          (0 until (numberOfChunks - 1)).map(_ => chunkSize) :+ lastChunkSize
        }
      val buffer = new Array[Byte](chunkSize)
      val chunks = new Array[FileChunkProto.FileChunk](numberOfChunks)
      val hashes = new Array[ByteString](numberOfChunks)
      for (i <- 0 until numberOfChunks) {
        val chunkLength = chunkSizes(i)
        fis.read(buffer, 0, chunkLength)
        val hash: ByteString = ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(buffer.take(chunkLength)))
        val chunk: FileChunkProto.FileChunk = FileChunkProto.FileChunk
          .newBuilder()
          .setHash(hash)
          .setData(ByteString.copyFrom(buffer, 0, chunkLength))
          .build()
        chunks.update(i, chunk)
        hashes.update(i, hash)
      }
      val file = FileProto.File
        .newBuilder()
        .setName(name)
        .setLength(fileLength)
        .addAllHashes(hashes.toVector.asJava)
        .build()
      (file, chunks)
    } finally {
      fis.close()
    }
  }

  def validateChunks(chunks: Array[FileChunkProto.FileChunk]): Boolean =
    chunks.indices.forall { (i: Int) =>
      val current: FileChunkProto.FileChunk = chunks(i)
      val bytes: Array[Byte]                = current.getData.toByteArray
      val hash: Array[Byte]                 = MessageDigest.getInstance("SHA-256").digest(bytes)
      hash sameElements current.getHash.toByteArray
    }

  def validateChunksInFile(chunks: Array[FileChunkProto.FileChunk], file: FileProto.File): Boolean = {
    file.getHashesList.toArray sameElements chunks.map(_.getHash)
  }

  def assembleFile(path: Path, file: FileProto.File, chunks: Array[FileChunkProto.FileChunk]): Unit = {
    val fos = new FileOutputStream(path.resolve(file.getName()).toFile)
    try {
      chunks.foreach { (c: FileChunkProto.FileChunk) =>
        fos.write(c.getData().toByteArray)
      }
    } finally {
      fos.close()
    }
  }

  @Test
  def roundTripFile(): Unit = {
    val (fileProto, chunks) = splitFile(testPDF)
    val destinationDir      = testStoreDir()
    val destinationFile     = destinationDir.resolve(fileProto.getName())
    Assert.assertTrue(validateChunks(chunks))
    Assert.assertTrue(validateChunksInFile(chunks, fileProto))
    assembleFile(destinationDir, fileProto, chunks)
    Assert.assertEquals(FileHash.fromPath(destinationFile).unsafeRun, testPDFHash)
  }
}
