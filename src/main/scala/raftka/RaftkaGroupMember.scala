package raftka

import akka.actor.ActorRef

class RaftkaGroupMember(aRef: ActorRef) {
  val actorRef: ActorRef = aRef
  var nextIndex: Int = 0

  def decrementNextIndex(): Unit = {
    nextIndex -= 1
  }

  def incrementNextIndex(): Unit = {
    nextIndex += 1
  }
}
