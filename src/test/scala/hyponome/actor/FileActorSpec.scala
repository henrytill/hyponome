package hyponome.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.util.{Failure, Success}

import Receptionist.{AddFile, RemoveFile, FindFile}
import hyponome.test._

class FileActorSpec(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("FileActorSpec"))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def withFileActor(testCode: ActorRef => Any): Unit = {
    val fileActor = system.actorOf(FileActor.props(testStorePath))
    try {
      testCode(fileActor); ()
    }
    finally {
      system.stop(fileActor)
      deleteFolder(testStorePath); ()
    }
  }

  "A FileActor" must {

    """respond with AddFileAck when attempting to add a file to the
    file store""" in withFileActor { fileActor =>
      fileActor ! AddFile(self, add)
      expectMsg(FileActor.AddFileAck(self, add))
    }

    """respond with PreviouslyAddedFile when attempting to add a file
   to the file store which has already been added""" in withFileActor { fileActor =>
      fileActor ! AddFile(self, add)
      expectMsg(FileActor.AddFileAck(self, add))
      fileActor ! AddFile(self, add)
      expectMsg(FileActor.PreviouslyAddedFile(self, add))
    }

    """respond with RemoveFileAck when attempting to remove a file
    from the file store""" in withFileActor { fileActor =>
      fileActor ! AddFile(self, add)
      expectMsg(FileActor.AddFileAck(self, add))
      fileActor ! RemoveFile(self, remove)
      expectMsg(FileActor.RemoveFileAck(self, remove))
    }

    """respond with PreviouslyRemovedFile when attempting to remove a
    file from the file store which has already been removed""" in withFileActor { fileActor =>
      fileActor ! AddFile(self, add)
      expectMsg(FileActor.AddFileAck(self, add))
      fileActor ! RemoveFile(self, remove)
      expectMsg(FileActor.RemoveFileAck(self, remove))
      fileActor ! RemoveFile(self, remove)
      expectMsg(FileActor.PreviouslyRemovedFile(self, remove))
    }

    """respond with StoreFile when sent a FindFile msg""" in withFileActor { fileActor =>
      fileActor ! AddFile(self, add)
      expectMsg(FileActor.AddFileAck(self, add))
      fileActor ! FindFile(self, testPDFHash)
      expectMsgType[FileActor.StoreFile]
    }
  }
}
