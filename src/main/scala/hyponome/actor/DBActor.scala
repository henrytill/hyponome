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

import akka.actor.{Actor, ActorRef, Props, Stash}
import akka.pattern.{pipe, PipeableFuture}
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.Future
import scala.util.{Success, Failure}
import slick.driver.H2Driver.api.TableQuery
import slick.driver.H2Driver.backend.DatabaseDef

import hyponome.core._
import hyponome.db._
import Controller.{PostWr, DeleteWr, GetWr}

object DBActor {

  final case object Ready

  sealed trait PostResponseWr extends Product with Serializable
  final case class PostAckWr(client: ActorRef, post: Post, status: PostStatus) extends PostResponseWr
  final case class PostFailWr(client: ActorRef, post: Post, e: Throwable) extends PostResponseWr

  sealed trait DeleteResponseWr extends Product with Serializable
  final case class DeleteAckWr(client: ActorRef, delete: Delete, status: DeleteStatus) extends DeleteResponseWr
  final case class DeleteFailWr(client: ActorRef, delete: Delete, e: Throwable) extends DeleteResponseWr

  final case class FileWr(client: ActorRef, file: Option[File])

  def props(dbDef: Function0[DatabaseDef], count: AtomicLong): Props = Props(new DBActor(dbDef, count))
}

class DBActor(dbDef: Function0[DatabaseDef], count: AtomicLong) extends Actor with Stash with HyponomeDB {

  import context.dispatcher
  import DBActor._

  val logger:  Logger             = LoggerFactory.getLogger(classOf[DBActor])
  val files:   TableQuery[Files]  = TableQuery[Files]
  val events:  TableQuery[Events] = TableQuery[Events]
  val db:      DatabaseDef        = dbDef()
  val counter: AtomicLong         = count

  override def preStart(): Unit = {
    val initFut: PipeableFuture[Ready.type] =
      exists.flatMap {
        case true  => this.syncCounter()
        case false => this.create()
      }.map { (_: Unit) =>
        logger.info("DB Initialized")
        Ready
      }.pipeTo(self)
  }

  override def postStop(): Unit = {
    this.close()
  }

  def prime: Receive = {
    case PostWr(c: ActorRef, p: Post) =>
      val addFut: PipeableFuture[PostResponseWr] =
        addFile(p).flatMap {
          case Created => Future(PostAckWr(c, p, Created))
          case Exists  => findFile(p.hash).map {
            case Some(f) => PostAckWr(c, p.mergeWithFile(f), Exists)
            case None    => PostFailWr(c, p, new NoSuchElementException)
          }
        }.recover {
          case ex => PostFailWr(c, p, ex)
        }.pipeTo(sender)
    case DeleteWr(c: ActorRef, d: Delete) =>
      val removeFut: PipeableFuture[DeleteResponseWr] =
        removeFile(d).map { (s: DeleteStatus) =>
          DeleteAckWr(c, d, s)
        }.recover {
          case ex => DeleteFailWr(c, d, ex)
        }.pipeTo(sender)
    case GetWr(c: ActorRef, h: SHA256Hash, n: Option[String]) =>
      val findFut: PipeableFuture[FileWr] =
        findFile(h).map { (f: Option[File]) =>
          FileWr(c, f)
        }.pipeTo(sender)
    case q: DBQuery =>
      val queryFuture: PipeableFuture[Seq[DBQueryResponse]] =
        runQuery(q).pipeTo(sender)
  }

  def pre: Receive = {
    case Ready =>
      unstashAll()
      context.become(prime)
    case msg => stash()
  }

  def receive: Receive = pre
}
