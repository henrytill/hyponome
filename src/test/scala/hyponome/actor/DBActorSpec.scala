package hyponome.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import Controller.{PostWr, DeleteWr, GetWr}
import hyponome.core._
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

    "respond with PostAckWr when adding a file" in withDBActor { dbActor =>
      dbActor ! PostWr(self, add)
      expectMsg(DBActor.PostAckWr(self, add, Created))
    }

    "respond with PostAckWr when adding a file that has already been added" in withDBActor { dbActor =>
      dbActor ! PostWr(self, add)
      expectMsg(DBActor.PostAckWr(self, add, Created))
      dbActor ! PostWr(self, add)
      expectMsg(DBActor.PostAckWr(self, add, Exists))
    }

    "respond with DeleteAckWr(self, remove) when removing a file" in withDBActor { dbActor =>
      dbActor ! PostWr(self, add)
      expectMsg(DBActor.PostAckWr(self, add, Created))
      dbActor ! DeleteWr(self, remove)
      expectMsg(DBActor.DeleteAckWr(self, remove, Deleted))
    }

    """respond with DeleteAckWr when removing a file that
    has already been removed""" in withDBActor { dbActor =>
      dbActor ! PostWr(self, add)
      expectMsg(DBActor.PostAckWr(self, add, Created))
      dbActor ! DeleteWr(self, remove)
      expectMsg(DBActor.DeleteAckWr(self, remove, Deleted))
      dbActor ! DeleteWr(self, remove)
      expectMsg(DBActor.DeleteAckWr(self, remove, NotFound))
    }

    """respond with the correct DBFile message when sent a GetWr
    message containing an added file's hash""" in withDBActor { dbActor =>
      dbActor ! PostWr(self, add)
      expectMsg(DBActor.PostAckWr(self, add, Created))
      dbActor ! GetWr(self, add.hash, add.name)
      expectMsg(DBActor.FileWr(self, Some(expected)))
    }

    """respond with the correct DBFile message when sent a GetWr
    message containing the hash of a file that has been removed""" in withDBActor { dbActor =>
      dbActor ! PostWr(self, add)
      expectMsg(DBActor.PostAckWr(self, add, Created))
      dbActor ! DeleteWr(self, remove)
      expectMsg(DBActor.DeleteAckWr(self, remove, Deleted))
      dbActor ! GetWr(self, add.hash, add.name)
      expectMsg(DBActor.FileWr(self, None))
    }
  }
}
