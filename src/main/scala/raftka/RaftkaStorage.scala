package raftka

import scala.collection.mutable.ListBuffer
import akka.actor.ActorRef

class RaftkaStorage {
  var logEntries = ListBuffer[RaftkaEntry]()

  def addEntry(term: Int, index: Int, entry: Any) = {
    logEntries += new RaftkaEntry(term, index, entry)
  }

  def addEntry(newEntry: RaftkaEntry) = {
    logEntries += newEntry
  }

  def removeLastEntry(): Unit = {
    logEntries.trimEnd(1)
  }

  def getLastEntry(): RaftkaEntry = {
    logEntries.last
  }

  def getEntryAtIndex(index: Int): RaftkaEntry = {
    logEntries(index)
  }

  def getTermforEntryIndex(index: Int): Option[Int] = {
    for (e <- logEntries) {
      if (e.getEntryIndex == index) { return Some(e.getEntryTerm)}
    }
    return None
  }

  def getEntriesFromIndex(index: Int): List[RaftkaEntry] = {
    logEntries.takeRight(logEntries.length - index + 1).toList
  }

  def getNumEntries(): Int = {
    return logEntries.length
  }

  def cleanLog(): Unit ={
    logEntries = new ListBuffer[RaftkaEntry]()
  }

  def getLog(): ListBuffer[RaftkaEntry] = {
    val log = logEntries.clone
    return log
  }

  def updateLog(newLog: ListBuffer[RaftkaEntry]) = {
    logEntries = newLog
  }

  def printLog(sender: ActorRef): Unit ={
    println(sender + " log:")
    for (entry <- logEntries) {
      entry.printEntry
    }
  }
}
