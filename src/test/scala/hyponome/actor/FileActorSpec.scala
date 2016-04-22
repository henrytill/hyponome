/*
 * Copyright 2016 Henry Till
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hyponome.actor

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, ImplicitSender}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

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
