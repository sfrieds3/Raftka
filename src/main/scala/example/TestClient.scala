package example

import raftka._

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.ListBuffer
import scala.util.Random

class TestClient extends Actor with RaftkaClient {
  //var clusterMembers: ListBuffer[ActorRef] = members
  var clusterMembers = ListBuffer[ActorRef]()

  override def receive = {

    case RaftkaMembers(memberList) =>
      clusterMembers = memberList

    case Start =>
      run
  }

  def run(): Unit = {
    val numTestMessages: Int = ConfigFactory.load.getInt("num-test-messages")

    for (i <- 0 until numTestMessages) {
      Thread.sleep(getRandomSleep)
      var newEntry = "Test" + (i+1).toString
      clusterMembers(getClusterMember) ! NewEntryFromClient(newEntry)
    }

    // give previous entry enough time to propagate to all members of cluster
    Thread.sleep(1000)

    // print logs for all members
    for (m <- clusterMembers) {
      m ! PrintLog
      Thread.sleep(1000)
    }

    // terminate system
    context.system.terminate
  }

  private def getRandomSleep(): Int = {
    Random.nextInt(2000)
  }

  private def getClusterMember(): Int = {
    var numClusterMembers = clusterMembers.length
    Random.nextInt(numClusterMembers)
  }

}
