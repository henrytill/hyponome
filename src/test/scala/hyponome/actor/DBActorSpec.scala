package hyponome.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import Controller.{AddFile, RemoveFile, FindFile}
import hyponome.test._

class DBActorSpec(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("DBActorSpec"))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def withDBActor(testCode: ActorRef => Any): Unit = {
    val dbActor = system.actorOf(DBActor.props(makeTestDB, makeCounter()))
    try {
      testCode(dbActor); ()
    }
    finally system.stop(dbActor) // dbActor's postStop() calls db.close
  }

  "A DBActor" must {

    "respond with AddFileAck when adding a file" in withDBActor { dbActor =>
      dbActor ! AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
    }

    "respond with PreviouslyAddedFile when adding a file that has already been added" in withDBActor { dbActor =>
      dbActor ! AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! AddFile(self, add)
      expectMsg(DBActor.PreviouslyAddedFile(self, add))
    }

    "respond with RemoveFileAck(self, remove) when removing a file" in withDBActor { dbActor =>
      dbActor ! AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! RemoveFile(self, remove)
      expectMsg(DBActor.RemoveFileAck(self, remove))
    }

    """respond with PreviouslyRemovedFile when removing a file that
    has already been removed""" in withDBActor { dbActor =>
      dbActor ! AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! RemoveFile(self, remove)
      expectMsg(DBActor.RemoveFileAck(self, remove))
      dbActor ! RemoveFile(self, remove)
      expectMsg(DBActor.PreviouslyRemovedFile(self, remove))
    }

    """respond with the correct DBFile message when sent a FindFile
    message containing an added file's hash""" in withDBActor { dbActor =>
      dbActor ! AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! FindFile(self, add.hash, add.name)
      expectMsg(DBActor.DBFile(self, add.hash, Some(expected)))
    }

    """respond with the correct DBFile message when sent a FindFile
    message containing the hash of a file that has been removed""" in withDBActor { dbActor =>
      dbActor ! AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! RemoveFile(self, remove)
      expectMsg(DBActor.RemoveFileAck(self, remove))
      dbActor ! FindFile(self, add.hash, add.name)
      expectMsg(DBActor.DBFile(self, add.hash, None))
    }
  }
}
