package hyponome.daemon

import java.nio.file.{Files, Path}
import java.util.{Random, UUID}

import fs2._
import fs2.util._
import hyponome._
import hyponome.daemon.stream._
import hyponome.test._
import org.junit.{Assert, Test}
import org.log4s._
import org.scalacheck.{Test => STest, Prop}

class StreamTest {

  private val logger = getLogger

  def log[A](prefix: String)(f: A => String): Pipe[Task, A, A] = _.evalMap { (a: A) =>
    Task.delay { logger.debug(s"$prefix ${f(a)}"); a }
  }

  def createTestFile(path: Path, bytes: Array[Byte]): Task[Path] =
    Task.delay { Files.write(path, bytes) }

  def now: Task[Double] =
    Task.now { System.nanoTime().toDouble }

  def uuid: Task[UUID] =
    Task.now { UUID.randomUUID() }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def randomByte(gen: Random = new Random(), arr: Array[Byte] = new Array[Byte](1024)): Stream[Task, Byte] =
    Stream
      .eval(Task.delay { gen.nextBytes(arr); arr })
      .flatMap((bytes: Array[Byte]) => Stream.emits(bytes))

  @Test
  def roundTripChunks() = {
    val Some(arrays) = sizedListOfSizedByteArrays(20, 512).sample
    val actual = Stream
      .emits(arrays)
      .through(rawByteArrayToFileChunk)
      .through(fileChunkToByteArray)
      .through(parseByteArrayToFileChunk)
      .through(fileChunkDataToByteArray)
      .runLog
      .unsafeRun
      .toList
    (arrays, actual).zipped.foreach { (a: Array[Byte], b: Array[Byte]) =>
      Assert.assertArrayEquals(a, b)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def prop_roundTripFile(implicit s: Suspendable[Task], c: Catchable[Task], strat: Strategy): Prop = {

    val dir: Path = Files.createTempDirectory("hyponome-test-files-")

    logger.debug(s"Dir: $dir")

    def comp(source: Path, destination: Path, bytes: Array[Byte]): Task[Unit] =
      Stream
        .eval(createTestFile(source, bytes))
        .through(processPath(512))
        .through(emitFile)
        .through(collectFiles)
        .through(writeFile(destination))
        .run

    Prop.forAll(genNEByteArray) { (bytes: Array[Byte]) =>
      (for {
        _           <- Task.now(logger.debug(s"====== NEW TEST ======"))
        length      <- Task.now(bytes.length)
        id          <- uuid
        source      <- Task.now(dir.resolve(s"$id-source"))
        destination <- Task.now(dir.resolve(s"$id-dest"))
        _           <- Task.now(logger.debug(s"Length:      $length bytes"))
        _           <- Task.now(logger.debug(s"Source:      $id-source"))
        _           <- Task.now(logger.debug(s"Destination: $id-dest"))
        start       <- now
        _           <- comp(source, destination, bytes)
        finish      <- now
        time        <- Task.now((finish - start) / 1000000)
        speed       <- Task.now((length.toDouble / 1000) / (time / 1000))
        _           <- Task.now(logger.debug(s"Stream completed in ${math.round(time)} ms"))
        _           <- Task.now(logger.debug(s"Stream rate: ${math.round(speed)} kB/s"))
        _           <- Task.now(logger.debug(s"Validating hashes..."))
        sourceHash  <- FileHash.fromPath(source)
        destHash    <- FileHash.fromPath(destination)
      } yield sourceHash == destHash).unsafeRun
    }
  }

  @Test
  def roundTripFiles() = {
    implicit val S: Strategy = Strategy.fromCachedDaemonPool("strategy-pool")
    val params               = STest.Parameters.defaultVerbose.withMinSize(256).withMaxSize(100000000).withMinSuccessfulTests(5)
    val result               = STest.check(params, prop_roundTripFile)
    Assert.assertTrue(result.passed)
  }
}
