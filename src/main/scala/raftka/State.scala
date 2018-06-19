package raftka

sealed trait RaftkaState
case object Leader extends RaftkaState
case object Follower extends RaftkaState
case object Candidate extends RaftkaState
