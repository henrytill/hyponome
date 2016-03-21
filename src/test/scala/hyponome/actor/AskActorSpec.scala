package hyponome.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import hyponome.core._
import hyponome.test._

class AskActorSpec(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("AskActorSpec"))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def withAskActor(testCode: ActorRef => Any): Unit = {
    val ctlActor = system.actorOf(Controller.props(makeTestDB, testStorePath))
    val askActor = system.actorOf(AskActor.props(ctlActor))
    try {
      testCode(askActor); ()
    }
    finally {
      system.stop(askActor)
      system.stop(ctlActor)
      deleteFolder(testStorePath); ()
    }
  }

  "An AskActor" must {

    """respond with PostAck when attempting to add a file to the
    store""" in withAskActor { askActor =>
      askActor ! add
      expectMsg(PostAck(added))
    }

    """respond with PostAck when attempting to add a file to the
    store which has already been added""" in withAskActor { askActor =>
      askActor ! add
      expectMsg(PostAck(added))
      askActor ! add
      expectMsg(PostAck(existed))
    }

    """respond with DeleteAck when attempting to remove a file from the
    store""" in withAskActor { askActor =>
      askActor ! add
      expectMsg(PostAck(added))
      askActor ! remove
      expectMsg(DeleteAck(remove, Deleted))
    }

    """respond with DeleteAck when attempting to remove a file from the
    store that has already been removed""" in withAskActor { askActor =>
      askActor ! add
      expectMsg(PostAck(added))
      askActor ! remove
      expectMsg(DeleteAck(remove, Deleted))
      askActor ! remove
      expectMsg(DeleteAck(remove, NotFound))
    }

    """respond with Result when sent a SHA256Hash""" in withAskActor { askActor =>
      askActor ! add
      expectMsg(PostAck(added))
      askActor ! add.hash
      expectMsgType[Result]
      askActor ! remove
      expectMsg(DeleteAck(remove, Deleted))
      askActor ! add.hash
      expectMsgType[Result]
    }
  }
}
