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

import akka.actor.{Actor, ActorRef, Props}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.core._

object Controller {

  final case class PostWr(client: ActorRef, p: Post)

  final case class DeleteWr(client: ActorRef, delete: Delete)

  final case class GetWr(client: ActorRef, hash: SHA256Hash, name: Option[String])

  def props(db: Function0[DatabaseDef], store: Path): Props = Props(new Controller(db, store))
}

class Controller(db: Function0[DatabaseDef], store: Path) extends Actor {

  import Controller._

  val counter:   AtomicLong = new AtomicLong()
  val dbActor:   ActorRef   = context.actorOf(DBActor.props(db, counter))
  val fileActor: ActorRef   = context.actorOf(FileActor.props(store))

  def prime: Receive = {
    // Adding files
    case p: Post =>
      fileActor ! PostWr(sender, p)
    case FileActor.PostAckWr(c: ActorRef, p: Post, _: PostStatus) =>
      dbActor ! PostWr(c, p)
    case FileActor.PostFailWr(c: ActorRef, p: Post, e: Throwable) =>
      c ! PostFail(e)
    case DBActor.PostAckWr(c: ActorRef, p: Post, s: PostStatus) =>
      c ! PostAck(Posted(p, s))
    case DBActor.PostFailWr(c: ActorRef, p: Post, e: Throwable) =>
      c ! PostFail(e)
    // Removing files
    case d: Delete =>
      dbActor ! DeleteWr(sender, d)
    case DBActor.DeleteAckWr(c: ActorRef, d: Delete, s: DeleteStatus) =>
      c ! DeleteAck(d, s)
    case DBActor.DeleteFailWr(c: ActorRef, d: Delete, e: Throwable) =>
      c ! DeleteFail(d, e)
    // Finding a file
    case h: SHA256Hash  =>
      dbActor ! GetWr(sender, h, None)
    case DBActor.FileWr(c: ActorRef, Some(f: File)) =>
      fileActor ! GetWr(c, f.hash, f.name)
    case DBActor.FileWr(c: ActorRef, None) =>
      c ! Result(None, None)
    case FileActor.ResultWr(c: ActorRef, f: Option[Path], n: Option[String]) =>
      c ! Result(f, n)
    // Query
    case q: DBQuery =>
      dbActor forward q
  }

  def receive: Receive = prime
}
