package hyponome.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import hyponome.core._
import java.net.InetAddress
import java.nio.file.{FileSystem, FileSystems, Path}
import java.util.UUID.randomUUID
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import slick.driver.H2Driver.api.Database

class AskActorSpec(_system: ActorSystem) extends TestKit(_system)
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

  val ip: Option[InetAddress] = Some(InetAddress.getByName("192.168.1.253"))

  val add = Addition(
    testPDF,
    getSHA256Hash(testPDF),
    testPDF.toFile.getName,
    "application/octet-stream",
    testPDF.toFile.length,
    ip
  )

  val remove = Removal(
    add.hash,
    add.remoteAddress
  )

  val expected = File(
    add.hash,
    add.name,
    add.contentType,
    add.length
  )

  def withAskActor(testCode: ActorRef => Any): Unit = {
    val dbName = randomUUID.toString
    val db = Database.forURL(
      url = s"jdbc:h2:mem:$dbName;CIPHER=AES",
      user = "hyponome",
      password = "hyponome hyponome", // password = "filepwd userpwd"
      driver = "org.h2.Driver",
      keepAliveConnection = true
    )
    val recActor = system.actorOf(Receptionist.props(db, tempStorePath))
    val askActor = system.actorOf(AskActor.props(recActor))
    try {
      testCode(askActor)
    }
    finally {
      system.stop(askActor)
      system.stop(recActor)
    }
  }

  "An AskActor" must {

    """respond with DeleteAck when attempting to delete a store""" in withAskActor { askActor =>
      askActor ! Delete
      expectMsg(DeleteAck)
    }

    """respond with CreateAck when attempting to create a new store if
    one doesn't already exist""" in withAskActor { askActor =>
      askActor ! Create
      expectMsg(CreateAck)
      askActor ! Delete
      expectMsg(DeleteAck)
    }

    """respond with AdditionAck when attempting to add a file to the
    store""" in withAskActor { askActor =>
      askActor ! Create
      expectMsg(CreateAck)
      askActor ! add
      expectMsg(AdditionAck(add))
      askActor ! Delete
      expectMsg(DeleteAck)
    }

    """respond with PreviouslyAdded when attempting to add a file to the
    store which has already been added""" in withAskActor { askActor =>
      askActor ! Create
      expectMsg(CreateAck)
      askActor ! add
      expectMsg(AdditionAck(add))
      askActor ! add
      expectMsg(PreviouslyAdded(add))
      askActor ! Delete
      expectMsg(DeleteAck)
    }

    """respond with RemovalAck when attempting to remove a file from the
    store""" in withAskActor { askActor =>
      askActor ! Create
      expectMsg(CreateAck)
      askActor ! add
      expectMsg(AdditionAck(add))
      askActor ! remove
      expectMsg(RemovalAck(remove))
      askActor ! Delete
      expectMsg(DeleteAck)
    }

    """respond with PreviouslyRemoved when attempting to remove a file from the
    store that has already been removed""" in withAskActor { askActor =>
      askActor ! Create
      expectMsg(CreateAck)
      askActor ! add
      expectMsg(AdditionAck(add))
      askActor ! remove
      expectMsg(RemovalAck(remove))
      askActor ! remove
      expectMsg(PreviouslyRemoved(remove))
      askActor ! Delete
      expectMsg(DeleteAck)
    }

    """respond with Result when sent a FindFile msg""" in withAskActor { askActor =>
      askActor ! Create
      expectMsg(CreateAck)
      askActor ! add
      expectMsg(AdditionAck(add))
      askActor ! FindFile(add.hash)
      expectMsgType[Result]
      askActor ! Delete
      expectMsg(DeleteAck)
    }
  }
}
