package hyponome.file

import com.typesafe.config.{Config, ConfigFactory}
import hyponome.core._
import java.net.InetAddress
import java.nio.file.{FileSystem, FileSystems, Path}
import org.scalatest.{Matchers, WordSpecLike}

class TestStore(p: Path) extends HyponomeFile {

  val storePath: Path = p
}

class HyponomeFileSpec extends WordSpecLike with Matchers {

  val add = Addition(
    SHA256Hash("01814411d889d10d474fff484e74c0f90ff5259e241de28851c2561b4ceb28a7"),
    "ShouldMLbeOO.pdf",
    "application/pdf",
    164943,
    Some(InetAddress.getByName("192.168.1.253"))
  )

  val hyponomeConfigFile: java.io.File = new java.io.File("hyponome.conf")

  val config: Config = ConfigFactory.parseFile(hyponomeConfigFile)

  val configStorePath: String = config.getString("file-store.path")

  val fs: FileSystem = FileSystems.getDefault()

  def withTestStoreInstance(testCode: TestStore => Any): Unit = {
    val t: TestStore = new TestStore(fs.getPath(configStorePath))
    testCode(t)
  }

  "An instance of a class that extends HyponomeFile" must {

    "have a getFilePath method" which {
      "returns the correct Path" in withTestStoreInstance { t =>
        val (dir, file) = add.hash.value.splitAt(2)
        val expected: Path = fs.getPath(configStorePath, dir, file).toAbsolutePath
        t.getFilePath(add.hash) should equal(expected)
      }
    }
  }
}
