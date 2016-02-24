package hyponome.actor

import akka.actor.{Actor, ActorRef, Props}
import hyponome.core

object AskActor {
  def props(recActor: ActorRef): Props = Props(new AskActor(recActor))
}

class AskActor(recActor: ActorRef) extends Actor {

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any"))
  def prime: Receive = {
    case core.Create                       => recActor ! Receptionist.Create(sender)
    case core.Delete                       => recActor ! Receptionist.Delete(sender)
    case a: core.Addition                  => recActor ! Receptionist.AddFile(sender, a)
    case r: core.Removal                   => recActor ! Receptionist.RemoveFile(sender, r)
    case core.FindFile(h: core.SHA256Hash) => recActor ! Receptionist.FindFile(sender, h)
    case _ =>
  }

  def receive: Receive = prime
}
