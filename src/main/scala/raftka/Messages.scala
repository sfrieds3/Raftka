package raftka

import akka.actor.ActorRef
import scala.collection.mutable.ListBuffer

sealed trait RaftkaMessages

/*Initial message for new actors*/
case object Uninitialized extends RaftkaMessages

/*AppendEtries is used to deliver updates from leader to members of the cluster, as well as for heartbeat messages.*/
final case class AppendEntries(term: Int, leaderId: ActorRef, prevLogIndex: Int, prevLogTerm: Int, entries: List[Any]) extends RaftkaMessages

/*AppendEntriesResponse is the response to an AppendEntries message.*/
final case class AppendEntriesResponse(term: Int, index: Int, success: Boolean) extends RaftkaMessages

/*RequestVote is sent by a candidate during a leader election.*/
final case class RequestVote(term: Int, candidateId: ActorRef, lastLogIndex: Int, lastLogTerm: Int) extends RaftkaMessages

/*RequestVoteResponse is the response sent by followers during an election.*/
final case class RequestVoteResponse(term: Int, voteGranted: Boolean) extends RaftkaMessages

/*UpdatedLog is a message to send full log to a follower from leader*/
final case class UpdatedLog(log: ListBuffer[RaftkaEntry])

/*CleanPrevLogEntry is sent by leader to any follower
whose log is not up to date - upon receipt, follower
will remove previous log entry*/
case object CleanPrevLogEntry
case object AckCleanPrevLogEntry

/*NewEntry is a new entry request from client*/
final case class NewEntryFromClient(entry: Any)

/*AckNewEntry is an ack sent to client once a new entry is replicated*/
final case object AckNewEntry
final case object NakNewEntry

// generalized case class for Raftka implementation
case class RaftkaMembers(memberList: ListBuffer[ActorRef]) extends RaftkaMessages
case object Start // to start client example
case object PrintLog // for client to tell RaftkaActor to print log
