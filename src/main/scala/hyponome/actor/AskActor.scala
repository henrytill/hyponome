package hyponome.actor

import akka.actor.{Actor, ActorRef, Props}

object AskActor {
  def props(recActor: ActorRef): Props = Props(new AskActor(recActor))
}

class AskActor(recActor: ActorRef) extends Actor {
  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any"))
  def receive: Receive = { case msg => recActor forward msg }
}
