package raftka

import akka.actor.{Actor, ActorRef}
import scala.collection.mutable.ListBuffer

trait RaftkaClient extends Actor {
  var members = ListBuffer[ActorRef]()

  def receive = {

      case RaftkaMembers(m) =>
        members = m

      case Start =>
        run

      case AckNewEntry =>
        // new entry replicated

      case NakNewEntry =>
        // new entry not replicated
  }

  def run

}
