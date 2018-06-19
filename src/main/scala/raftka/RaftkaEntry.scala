package raftka

class RaftkaEntry(entryTerm: Int, entryIndex: Int, entry: Any) {
  def getEntry(): Any = {
    return entry
  }

  def getEntryTerm(): Int = {
    return entryTerm
  }

  def getEntryIndex(): Int = {
    return entryIndex
  }

  def printEntry(): Unit = {
    println("term: " + entryTerm + " | index: " + entryIndex + " | entry: " + entry)
  }
}
