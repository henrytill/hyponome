package hyponome.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import hyponome.core._
import java.net.InetAddress
import java.nio.file._
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

  val fs: FileSystem = FileSystems.getDefault()

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

  def withDBActor(testCode: ActorRef => Any): Unit = {
    val dbName = randomUUID.toString
    val db = Database.forURL(
      url = s"jdbc:h2:mem:$dbName;CIPHER=AES",
      user = "hyponome",
      password = "hyponome hyponome", // password = "filepwd userpwd"
      driver = "org.h2.Driver",
      keepAliveConnection = true
    )
    val dbActor = system.actorOf(DBActor.props(db))
    try {
      testCode(dbActor)
      ()
    }
    finally system.stop(dbActor) // dbActor's postStop() calls db.close
  }

  "A DBActor" must {

    """respond with CreateDBAck(self) when attempting to create a new DB if
    one doesn't already exist""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBAck(self))
    }

    """respond with CreateDBFail when attempting to create a new DB if
    one already exists""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBAck(self))
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBFail(self))
    }

    "respond with AddFileAck when adding a file" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBAck(self))
      dbActor ! DBActor.AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
    }

    "respond with PreviouslyAddedFile when adding a file that has already been added" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBAck(self))
      dbActor ! DBActor.AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! DBActor.AddFile(self, add)
      expectMsg(DBActor.PreviouslyAddedFile(self, add))
    }

    "respond with RemoveFileAck(self, remove) when removing a file" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBAck(self))
      dbActor ! DBActor.AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! DBActor.RemoveFile(self, remove)
      expectMsg(DBActor.RemoveFileAck(self, remove))
    }

    """respond with PreviouslyRemovedFile when removing a file that
    has already been removed""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBAck(self))
      dbActor ! DBActor.AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! DBActor.RemoveFile(self, remove)
      expectMsg(DBActor.RemoveFileAck(self, remove))
      dbActor ! DBActor.RemoveFile(self, remove)
      expectMsg(DBActor.PreviouslyRemovedFile(self, remove))
    }

    """respond with the correct DBFile message when sent a FindFile
    message containing an added file's hash""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBAck(self))
      dbActor ! DBActor.AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! DBActor.FindFile(self, add.hash)
      expectMsg(DBActor.DBFile(self, add.hash, Some(expected)))
    }

    """respond with the correct DBFile message when sent a FindFile
    message containing the hash of a file that has been removed""" in withDBActor { dbActor =>
      dbActor ! DBActor.CreateDB(self)
      expectMsg(DBActor.CreateDBAck(self))
      dbActor ! DBActor.AddFile(self, add)
      expectMsg(DBActor.AddFileAck(self, add))
      dbActor ! DBActor.RemoveFile(self, remove)
      expectMsg(DBActor.RemoveFileAck(self, remove))
      dbActor ! DBActor.FindFile(self, add.hash)
      expectMsg(DBActor.DBFile(self, add.hash, None))
    }
  }
}
