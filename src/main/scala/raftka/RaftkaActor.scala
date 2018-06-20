package raftka

import client._

import akka.actor.{ Actor, ActorRef, ActorSystem, FSM, Props }
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.{ LinkedHashMap, ListBuffer, Map }
import scala.concurrent.duration._
import scala.util.Random

class RaftkaActor extends FSM[RaftkaState, RaftkaMessages] {
  final val TEST: Boolean = ConfigFactory.load.getBoolean("TEST")
  final val DEBUG: Boolean = ConfigFactory.load.getBoolean("DEBUG")

  final val numReplicas: Int = ConfigFactory.load.getInt("num-replicas")
  final val defaultHeartbeatInterval: Int = ConfigFactory.load.getInt(
      "default-heartbeat-interval-ms")

  var logEntries = new RaftkaStorage
  var memberList = ListBuffer[RaftkaGroupMember]()
  var numReplicasForQuorum: Int = (numReplicas + 1) / 2 // +1 to include self
  var currentTerm: Int = 0 // current term
  var lastLogIndex: Int = 0 // last log index that has been committed
  var lastLogTerm: Int = 0
  var leaderVoteCount: Int = 0
  var votedFor: ActorRef = self // placeholder
  var votedThisTerm: Boolean = false
  var leaderThisTerm: ActorRef = self // placeholder

  startWith(Follower, Uninitialized)

  onTransition {

    case Follower -> Candidate =>
      if (DEBUG) {println(self + " sending RequestVote")}
      leaderVoteCount = 1 // candidate votes for itself first
      votedThisTerm = true // candidate has voted for itself
      sendMessage(RequestVote(currentTerm + 1, self, lastLogIndex, lastLogTerm))

    case Candidate -> Leader =>
      votedThisTerm = false
      leaderVoteCount = 0
      if (DEBUG) {println(self + " I am leader!")}
      sendMessage(AppendEntries(currentTerm, self, lastLogIndex, lastLogTerm, List()))

    case Candidate -> Follower =>
      leaderVoteCount = 0
      votedThisTerm = false
  }

  when(Follower, stateTimeout = getElectionTimeout) {

    case Event(RaftkaMembers(members), _) =>
      addMembers(members, self)
      if (DEBUG) {println(memberList)}
      stay

    case Event(StateTimeout, _) =>
      if (DEBUG) {println(self + " follower timed out, starting election.")}
      goto(Candidate) using Uninitialized

    case Event(RequestVote(term, candidateId, lastLogIndex, lastLogTerm), _) =>
      if (DEBUG) {println(self + " received vote request from candidate: " + candidateId)}
      if (!votedThisTerm) {
        if (voteValid(term, lastLogIndex, lastLogTerm)) {
          votedThisTerm = true
          currentTerm = term
          votedFor = sender
          sender ! RequestVoteResponse(term, true)
        } else {
          sender ! RequestVoteResponse(term, false)
        }
        goto(Follower) using Uninitialized // use go to in order to change timeout
      } else {
        sender ! RequestVoteResponse(term, false) // do not vote for process requesting
        goto(Follower) using Uninitialized // have already voted this round, cannot vote again
      }

    case Event(RequestVoteResponse(term, voteGranted), _) =>
      stay // leader already elected, can ignore additional votes

    case Event(AppendEntries(term, leaderId, prevLogIndex, prevLogTerm, entries), _) =>
      leaderThisTerm = sender
      votedThisTerm = false
      if (DEBUG) {println(self + " received AppendEntries from " +
                          leaderId + ", term: " +
                          currentTerm + " prevLogIndex: " +
                          prevLogIndex + " prevLogTerm: " +
                          prevLogTerm)}
      if (prevLogTerm == this.lastLogTerm && prevLogIndex == this.lastLogIndex) {
        if (entries.length > 0) {
          lastLogTerm = currentTerm
          for (e <- entries) {
            if (DEBUG) {println(self + " received new entries from AppendEntries: " + e)}
            lastLogIndex += 1
            writeToLog(lastLogTerm, lastLogIndex, e)
          }
        }
        sender ! AppendEntriesResponse(currentTerm, lastLogIndex, true)

        // randomly remove last entry ***TESTING PURPOSES ONLY***
        if (TEST && returnTestBool && logEntries.getNumEntries > 2) {
          if (DEBUG) {println(self + " removing last entry")}
          logEntries.removeLastEntry
          lastLogIndex = logEntries.getLastEntry.getEntryIndex
          lastLogTerm = logEntries.getLastEntry.getEntryTerm
        }
      } else {
        if (DEBUG) {println(self + " sending FALSE AppendEntriesResponse, lastLogIndex: " +
                            lastLogIndex + " lastLogTerm: " + lastLogTerm)}
        sender ! AppendEntriesResponse(lastLogTerm, lastLogIndex, false)
      }
      stay

    case Event(CleanPrevLogEntry, _) =>
      // remove previous log entries
      logEntries.cleanLog
      sender ! AckCleanPrevLogEntry
      stay

    case Event(UpdatedLog(newLog), _) =>
      logEntries.updateLog(newLog)
      lastLogIndex = logEntries.getLastEntry.getEntryIndex
      lastLogTerm = logEntries.getLastEntry.getEntryTerm
      stay

    case Event(NewEntryFromClient(entry), _) =>
      leaderThisTerm ! NewEntryFromClient(entry)
      stay

    case Event(PrintLog, _) =>
      if (DEBUG) {printLog}
      stay
  }

  when(Leader, stateTimeout = Duration(defaultHeartbeatInterval, MILLISECONDS)) {

    case Event(StateTimeout, _) =>
      // randomly time out leader -- ***TESTING PURPOSES ONLY***
      if (TEST && returnTestBool()) {
        if (DEBUG) {println(self + " (Leader) is stopping")}
        goto(Follower) using Uninitialized
      } else {
        sendMessage(AppendEntries(currentTerm, self, lastLogIndex, lastLogTerm, List()))
        stay using Uninitialized
      }

    case Event(AppendEntriesResponse(term, index, success), _) if success =>
      if (DEBUG) {println(self + " received TRUE AppendEntriesResponse from " + sender)}

      for (m <- memberList) {
        // update leader log of lastLogIndex
        if (m.actorRef == sender) {
          m.nextIndex = lastLogIndex + 1
        }
      }
      stay

    case Event(AppendEntriesResponse(term, index, success), _) if !success =>
      if (DEBUG) {println(self + " received FALSE AppendEntriesResponse from " + sender)}
      // send full log from leader to sender (follower)
      val updatedLog = logEntries.getLog
      sender ! UpdatedLog(updatedLog)
      stay

    case Event(AckCleanPrevLogEntry, _) =>
      // send previous log entry to sender
      val updatedLog = logEntries.getLog
      sender ! UpdatedLog(updatedLog)
      stay

    case Event(NewEntryFromClient(entry), _) =>
      if (DEBUG) {println(self + " is leader, received new entry: " +
                          entry + " from: " + sender)}
      writeToLog(currentTerm, lastLogIndex + 1, entry)
      sendMessage(AppendEntries(currentTerm, self, lastLogIndex, lastLogTerm, List(entry)))
      lastLogIndex += 1
      lastLogTerm = currentTerm
      stay

    case Event(RequestVoteResponse(term, voteGranted), _) =>
      stay // currently the leader, do not need to respond to vote request

    case Event(PrintLog, _) =>
      if (DEBUG) {printLog}
      stay
  }

  when(Candidate, stateTimeout = getElectionTimeout) {

    case Event(StateTimeout, _) =>
      if (DEBUG) {println(self + " candidate timed out, going to Follower")}
      goto(Follower) using Uninitialized

    case Event(RequestVoteResponse(term, voteGranted), _) if voteGranted =>
      if (DEBUG) {println(self + " received requestVoteResponse")}
      leaderVoteCount += 1
      if (DEBUG) {println(self + " leaderVoteCount: " + leaderVoteCount + " for term: " + term)}
      // numReplicasForQuorum is FLOOR, so need greater than that to win
      if (leaderVoteCount > numReplicasForQuorum) {
        leaderVoteCount = 0
        currentTerm = term
        if (DEBUG) {println("new term: " + currentTerm)}
        goto(Leader) using Uninitialized
      }else {
        stay
      }

    case Event(RequestVoteResponse(term, voteGranted), _) if !voteGranted =>
      stay

    case Event(RequestVote(term, candidateId, lastLostIndex, lastLogTerm), _) =>
      if (DEBUG) {println(self + " received RequestVote from: " + candidateId)}
      // do not respond to request vote because have already voted
      stay

    case Event(AppendEntries(term, leaderId, prevLogIndex, prevLogTerm, entries), _) =>
      if (DEBUG) {println(self + " is candidate, received Appendentries from " + leaderId)}
      if (term >= currentTerm) {
        currentTerm = term // update canidate to match new term
        goto(Follower) using Uninitialized
      } else {
        stay
      }

    case Event(PrintLog, _) =>
      if (DEBUG) {printLog}
      stay

    case Event(NewEntryFromClient(entry), _) =>
      sender ! NakNewEntry
      stay // do not handle new entry from client when candidate
  }

  initialize()

  // helper functions

  private def getElectionTimeout(): FiniteDuration = {
    // electionTimeout between 150-300ms
    Duration(Random.nextInt(150) + 150, MILLISECONDS)
  }

  private def addMembers(members: ListBuffer[ActorRef], sender: ActorRef): Unit = {
    for (member <- members) if (member != sender) {
      memberList += new RaftkaGroupMember(member)
    }

    if (DEBUG) {println("memberList: " + memberList)}
  }

  private def sendMessage(msg: RaftkaMessages): Unit = {
    if (DEBUG) {println("sending message: " + msg)}
    for (m <- memberList) {
      m.actorRef ! msg
    }
  }

  private def writeToLog(logEntryTerm: Int, logEntryIndex: Int, newEntry: Any): Unit = {
    if (DEBUG) {println(self + " updated logEntries for term: " +
                logEntryTerm + " -> index " +
                logEntryIndex + " | " +
                newEntry)}
    logEntries.addEntry(logEntryTerm, logEntryIndex, newEntry)
  }

  private def voteValid(term: Int, lastLogIndex: Int, lastLogTerm: Int): Boolean = {
    if (term > currentTerm && lastLogIndex >= this.lastLogIndex
                            && lastLogTerm >= this.lastLogTerm) {
      true
    } else {
      false
    }
  }

  private def printLog(): Unit ={
    logEntries.printLog(self)
  }

  // for testing purposes -- returns boolean to e.g. kill process
  private def returnTestBool(): Boolean ={
    if (Random.nextInt(100) < 30) {
      return true
    } else {
      return false
    }
  }
}

object Raftka extends App {
  final val CLIENTCLASS: String = ConfigFactory.load.getString("client-class")
  final val CLIENTPACKAGE: String = ConfigFactory.load.getString("client-package")
  final val NUMREPLICAS: Int = ConfigFactory.load.getInt("num-replicas")

  var replicaActors = ListBuffer[ActorRef]()

  println("Starting Raftka with client class: " + CLIENTCLASS)

  val system = ActorSystem("Raftka")
  val clientClass: String = CLIENTPACKAGE + "." + CLIENTCLASS
  val clientActor = system.actorOf(Props(Class.forName(clientClass)), name = "clientActor")

  // create replicas
  for (i <- 1 to NUMREPLICAS) {
    val replicaActor = system.actorOf(Props[RaftkaActor])
    replicaActors += replicaActor
  }

  // mend list of all replicas to each replica
  for (i <- 1 to NUMREPLICAS) {
    replicaActors(i-1) ! RaftkaMembers(replicaActors)
  }

  // initialize client
  clientActor ! RaftkaMembers(replicaActors)
  clientActor ! Start
}
