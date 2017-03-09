package hyponome.test

import hyponome._
import java.nio.file.{FileSystem, FileSystems, Path}

trait TestData {
  private val fs: FileSystem = FileSystems.getDefault

  val testPDF: Path                      = fs.getPath(getClass.getResource("/test.pdf").getPath)
  val testPDFHash: FileHash              = FileHash.fromHex("eba205fb9114750b2ce83db62f9c2a15dd068bcba31a2de32d8df7f7c8d85441")
  val testPDFName: Option[String]        = Some(testPDF.toFile.getName)
  val testPDFContentType: Option[String] = Some("application/octet-stream")
  val testPDFLength: Long                = testPDF.toFile.length
  val testUser: User                     = User("Alice", "alice@zz.cc")
  val testMetadata: Metadata             = Metadata("This is some metadata")
  val testMessageAdd: Message            = Message("I'm adding this file")
  val testMessageRemove: Message         = Message("I'm removing this file")

  case class TestData(file: Path,
                      hash: FileHash,
                      name: Option[String],
                      contentType: Option[String],
                      length: Long,
                      metadata: Metadata,
                      user: User,
                      message: Message)

  val testData = TestData(testPDF, testPDFHash, testPDFName, testPDFContentType, testPDFLength, testMetadata, testUser, testMessageAdd)

  val testFile = File(testPDFHash, testPDFName, testPDFContentType, testPDFLength, testMetadata)
}
