package hyponome.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import hyponome.core._
import java.util.UUID.randomUUID
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import slick.driver.H2Driver.api._

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
    val dbName = randomUUID.toString
    val db = Database.forURL("jdbc:h2:mem:" + dbName, driver="org.h2.Driver", keepAliveConnection = true)
    val dbActor = system.actorOf(DBActor.props(db))
    try {
      testCode(dbActor)
    }
    finally system.stop(dbActor) // dbActor's postStop() calls db.close
  }

  val add = Addition(
    SHA256Hash("01814411d889d10d474fff484e74c0f90ff5259e241de28851c2561b4ceb28a7"),
    "ShouldMLbeOO.pdf",
    "application/pdf",
    164943,
    "192.168.1.253"
  )

  val remove = Removal(
    SHA256Hash("01814411d889d10d474fff484e74c0f90ff5259e241de28851c2561b4ceb28a7"),
    "192.168.1.253"
  )

  val expected = File(
    SHA256Hash("01814411d889d10d474fff484e74c0f90ff5259e241de28851c2561b4ceb28a7"),
    "ShouldMLbeOO.pdf",
    "application/pdf",
    164943
  )

  "A DBActor" must {

    """respond with CreateDBAck when attempting to create a new DB if
    one doesn't already exist""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBAck)
    }

    """respond with CreateDBFail when attempting to create a new DB if
    one already exists""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBAck)
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBFail)
    }

    "respond with AddFileAck when adding a file" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBAck)
      dbActor ! DBActor.AddFile(add)
      expectMsg(DBActor.AddFileAck)
    }

    "respond with AddFileFail when adding a file that has already been added" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBAck)
      dbActor ! DBActor.AddFile(add)
      expectMsg(DBActor.AddFileAck)
      dbActor ! DBActor.AddFile(add)
      expectMsg(DBActor.AddFileFail)
    }

    "respond with RemoveFileAck when removing a file" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBAck)
      dbActor ! DBActor.AddFile(add)
      expectMsg(DBActor.AddFileAck)
      dbActor ! DBActor.RemoveFile(remove)
      expectMsg(DBActor.RemoveFileAck)
    }

    """respond with RemoveFileFail when removing a file that has already
    been removed""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBAck)
      dbActor ! DBActor.AddFile(add)
      expectMsg(DBActor.AddFileAck)
      dbActor ! DBActor.RemoveFile(remove)
      expectMsg(DBActor.RemoveFileAck)
      dbActor ! DBActor.RemoveFile(remove)
      expectMsg(DBActor.RemoveFileFail)
    }

    """respond with the correct File message when sent a FindFile
    message containing an added file's hash""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBAck)
      dbActor ! DBActor.AddFile(add)
      expectMsg(DBActor.AddFileAck)
      dbActor ! DBActor.FindFile(add.hash)
      expectMsg(expected)
    }

    """respond with a FileNotExpected message when sent a FindFile
    message containing the hash of a file that has been removed""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB
      expectMsg(DBActor.CreateDBAck)
      dbActor ! DBActor.AddFile(add)
      expectMsg(DBActor.AddFileAck)
      dbActor ! DBActor.RemoveFile(remove)
      expectMsg(DBActor.RemoveFileAck)
      dbActor ! DBActor.FindFile(add.hash)
      expectMsg(DBActor.FileNotFound)
    }
  }
}
