package hyponome.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.util.{Failure, Success}

import Controller.{PostWr, DeleteWr, GetWr}
import hyponome.core._
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

    """respond with PostAckWr when attempting to add a file to the
    file store""" in withFileActor { fileActor =>
      fileActor ! PostWr(self, add)
      expectMsg(FileActor.PostAckWr(self, add, Created))
    }

    """respond with PostAckWr when attempting to add a file
   to the file store which has already been added""" in withFileActor { fileActor =>
      fileActor ! PostWr(self, add)
      expectMsg(FileActor.PostAckWr(self, add, Created))
      fileActor ! PostWr(self, add)
      expectMsg(FileActor.PostAckWr(self, add, Exists))
    }

    """respond with DeleteAckWr when attempting to remove a file
    from the file store""" in withFileActor { fileActor =>
      fileActor ! PostWr(self, add)
      expectMsg(FileActor.PostAckWr(self, add, Created))
      fileActor ! DeleteWr(self, remove)
      expectMsg(FileActor.DeleteAckWr(self, remove, Deleted))
    }

    """respond with DeleteNotFoundWr when attempting to remove a
    file from the file store which has already been removed""" in withFileActor { fileActor =>
      fileActor ! PostWr(self, add)
      expectMsg(FileActor.PostAckWr(self, add, Created))
      fileActor ! DeleteWr(self, remove)
      expectMsg(FileActor.DeleteAckWr(self, remove, Deleted))
      fileActor ! DeleteWr(self, remove)
      expectMsg(FileActor.DeleteAckWr(self, remove, NotFound))
    }

    """respond with ResultWr when sent a GetWr msg""" in withFileActor { fileActor =>
      fileActor ! PostWr(self, add)
      expectMsg(FileActor.PostAckWr(self, add, Created))
      fileActor ! GetWr(self, add.hash, add.name)
      expectMsgType[FileActor.ResultWr]
    }
  }
}
