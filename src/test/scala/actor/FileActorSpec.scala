package hyponome.actor

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, ImplicitSender}
import hyponome.core._
import java.nio.file._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.util.{Failure, Success}

class FileActorSpec(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("FileActorSpec"))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val fs: FileSystem = FileSystems.getDefault()

  val tempStorePath: Path = fs.getPath("/tmp/hyponome/store")

  val testPDF: Path = {
    val s: String = getClass.getResource("/test.pdf").getPath
    fs.getPath(s)
  }

  val testPDFHash = SHA256Hash(
    "eba205fb9114750b2ce83db62f9c2a15dd068bcba31a2de32d8df7f7c8d85441"
  )

  def withFileActor(testCode: ActorRef => Any): Unit = {
    val fileActor = system.actorOf(FileActor.props(tempStorePath))
    try {
      testCode(fileActor)
    }
    finally system.stop(fileActor)
  }

  "A FileActor" must {

    """respond with DeleteStoreAck when attempting to delete a store""" in withFileActor { fileActor =>
      fileActor ! FileActor.DeleteStore
      expectMsg(FileActor.DeleteStoreAck)
    }

    """respond with CreateStoreAck when attempting to create a store""" in withFileActor { fileActor =>
      fileActor ! FileActor.CreateStore
      expectMsg(FileActor.CreateStoreAck)
      fileActor ! FileActor.DeleteStore
      expectMsg(FileActor.DeleteStoreAck)
    }

    """respond with AddFileAck when attempting to add a file to the
    store""" in withFileActor { fileActor =>
      fileActor ! FileActor.CreateStore
      expectMsg(FileActor.CreateStoreAck)
      fileActor ! FileActor.AddFile(testPDFHash, testPDF)
      expectMsg(FileActor.AddFileAck(testPDFHash, testPDF))
      fileActor ! FileActor.DeleteStore
      expectMsg(FileActor.DeleteStoreAck)
    }

    """respond with AddFileFail when attempting to add a file to the
    store which has already been added""" in withFileActor { fileActor =>
      fileActor ! FileActor.CreateStore
      expectMsg(FileActor.CreateStoreAck)
      fileActor ! FileActor.AddFile(testPDFHash, testPDF)
      expectMsg(FileActor.AddFileAck(testPDFHash, testPDF))
      fileActor ! FileActor.AddFile(testPDFHash, testPDF)
      expectMsgType[FileActor.AddFileFail]
      fileActor ! FileActor.DeleteStore
      expectMsg(FileActor.DeleteStoreAck)
    }

    """respond with RemoveFileAck when attempting to remove a file
    from the store""" in withFileActor { fileActor =>
      fileActor ! FileActor.CreateStore
      expectMsg(FileActor.CreateStoreAck)
      fileActor ! FileActor.AddFile(testPDFHash, testPDF)
      expectMsg(FileActor.AddFileAck(testPDFHash, testPDF))
      fileActor ! FileActor.RemoveFile(testPDFHash)
      expectMsg(FileActor.RemoveFileAck(testPDFHash))
      fileActor ! FileActor.DeleteStore
      expectMsg(FileActor.DeleteStoreAck)
    }

    """respond with RemoveFileFail when attempting to remove a file
    from the store which has already been removed""" in withFileActor { fileActor =>
      fileActor ! FileActor.DeleteStore
      expectMsg(FileActor.DeleteStoreAck)
      fileActor ! FileActor.CreateStore
      expectMsg(FileActor.CreateStoreAck)
      fileActor ! FileActor.AddFile(testPDFHash, testPDF)
      expectMsg(FileActor.AddFileAck(testPDFHash, testPDF))
      fileActor ! FileActor.RemoveFile(testPDFHash)
      expectMsg(FileActor.RemoveFileAck(testPDFHash))
      fileActor ! FileActor.RemoveFile(testPDFHash)
      expectMsgType[FileActor.RemoveFileFail]
    }

    """respond with StoreFile when sent a FindFile msg""" in withFileActor { fileActor =>
      fileActor ! FileActor.DeleteStore
      expectMsg(FileActor.DeleteStoreAck)
      fileActor ! FileActor.CreateStore
      expectMsg(FileActor.CreateStoreAck)
      fileActor ! FileActor.AddFile(testPDFHash, testPDF)
      expectMsg(FileActor.AddFileAck(testPDFHash, testPDF))
      fileActor ! FileActor.FindFile(testPDFHash)
      expectMsgType[FileActor.StoreFile]
    }
  }
}
