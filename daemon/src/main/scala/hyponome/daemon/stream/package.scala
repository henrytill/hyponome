package hyponome.daemon

import java.nio.file.Path
import java.security.MessageDigest

import com.google.protobuf.ByteString
import fs2._
import fs2.util._
import hyponome.daemon.util._
import hyponome.protobuf.FileProto.File
import hyponome.protobuf.FileInfoProto.FileInfo
import hyponome.protobuf.FileChunkProto.FileChunk

import scala.collection.JavaConverters._

package object stream {

  /* FileChunk combinators */

  def fileChunkToByteArray[F[_]]: Pipe[F, FileChunk, Array[Byte]] = _.map { (fc: FileChunk) =>
    fc.toByteArray
  }

  def fileChunkDataToByteArray[F[_]]: Pipe[F, FileChunk, Array[Byte]] = _.map { (fc: FileChunk) =>
    fc.getData.toByteArray
  }

  val parseByteArrayToFileChunk: Pipe[Task, Array[Byte], FileChunk] = _.evalMap { (bytes: Array[Byte]) =>
    Task.delay { FileChunk.parseFrom(bytes) }
  }

  val rawByteArrayToFileChunk: Pipe[Task, Array[Byte], FileChunk] = _.evalMap { (bytes: Array[Byte]) =>
    Task.delay {
      FileChunk
        .newBuilder()
        .setHash(ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(bytes)))
        .setData(ByteString.copyFrom(bytes))
        .build()
    }
  }

  def pathToFileChunks(path: Path, chunkSize: Int)(implicit s: Suspendable[Task]): Stream[Task, FileChunk] =
    io.file
      .readAll(path, 8192)
      .vectorChunkN(chunkSize)
      .map(_.toArray)
      .through(rawByteArrayToFileChunk)

  def byteToFileChunk(chunkSize: Int): Pipe[Task, Byte, FileChunk] =
    _.vectorChunkN(chunkSize)
      .map(_.toArray)
      .through(rawByteArrayToFileChunk)

  /* File combinators */

  def fileChunkToFile: Pipe[Task, FileChunk, File] = _.evalMap { (chunk: FileChunk) =>
    Task.delay { File.newBuilder().setChunk(chunk).build() }
  }

  def rawEmit(chunkSize: Int): Pipe[Task, Path, File] = _.flatMap { (path: Path) =>
    pathToFileChunks(path, chunkSize).through(fileChunkToFile)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def fileChunksToFileInfo(name: String, length: Long): Pipe[Task, FileChunk, (Vector[FileChunk], FileInfo)] =
    _.fold(Vector.empty[FileChunk]) { (acc: Vector[FileChunk], a: FileChunk) =>
      acc :+ a
    }.evalMap { (chunks: Vector[FileChunk]) =>
      Task.delay {
        (chunks,
         FileInfo
           .newBuilder()
           .setName(name)
           .setLength(length)
           .addAllHashes(chunks.map(_.getHash).asJava)
           .build())
      }
    }

  def fileToByte: Pipe[Task, File, Byte] =
    _.filter((file: File) => file.hasChunk)
      .flatMap((file: File) => Stream.emits(file.getChunk.getData.toByteArray))

  def fileToByteArray: Pipe[Task, File, Array[Byte]] =
    _.filter((file: File) => file.hasChunk)
      .fold(Vector.empty[Byte])((acc: Vector[Byte], file: File) => acc ++ file.getChunk.getData.toByteArray)
      .map(_.toArray)

  /* Higher-level combinators */

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def processPath(chunkSize: Int): Pipe[Task, Path, (Vector[FileChunk], FileInfo)] = _.flatMap { (path: Path) =>
    pathToFileChunks(path, chunkSize).through(fileChunksToFileInfo(path.getFileName.toString, path.toFile.length))
  }

  val emitFile: Pipe[Task, (Vector[FileChunk], FileInfo), File] = _.flatMap {
    case (chunks, info) =>
      Stream.eval { Task.delay { File.newBuilder().setFile(info).build() } } ++
        Stream.emits(chunks).evalMap { (chunk: FileChunk) =>
          Task.delay { File.newBuilder().setChunk(chunk).build() }
        }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def collectFiles: Pipe[Task, File, (Vector[FileChunk], Option[FileInfo])] = _.fold((Vector.empty[FileChunk], Option.empty[FileInfo])) {
    (acc: (Vector[FileChunk], Option[FileInfo]), file: File) =>
      val (chunks, maybeInfo) = acc
      (file.hasFile, file.hasChunk) match {
        case (true, false) => (chunks, Option(file.getFile))
        case (false, true) => (chunks :+ file.getChunk, maybeInfo)
        case (x, y)        => throw new Exception(s"collectFile: hasFile = $x, hasChunk = $y")
      }
  }

  def extractDataAsByteArray: Pipe[Task, (Vector[FileChunk], Option[FileInfo]), Array[Byte]] =
    _.map {
      case (chunks, _) => chunks.toArray.flatMap(_.getData.toByteArray)
    }

  def writeFile(dest: Path)(implicit s: Suspendable[Task]): Pipe[Task, (Vector[FileChunk], Option[FileInfo]), Unit] =
    _.flatMap {
      case (chunks, _) => Stream.emits(chunks.toArray.flatMap(_.getData.toByteArray))
    }.to(io.file.writeAll(dest))

  /* Misc. */

  def byteArraytoDelimitedByteArray[F[_]]: Pipe[F, Array[Byte], Array[Byte]] = _.map { (bytes: Array[Byte]) =>
    intToByteArray(bytes.length) ++ bytes
  }
}
