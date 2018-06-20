# Raftka

**Not intended for production use**

This is an implementation of Raft in Scala using Akka (that is *very much* a work in
progress). I named it Raftka, because I thought it should have a cool name.

I tried to keep this as faithful as possible to the paper's implementation, but
there is still a lot of room for improvement. In its current implementation,
Raftka acts as distributed data store (data type for the store is specified in
RaftkaClient implementation, but can be Any).

## Implementation Details

* Messages: list of messagaes to be sent between actors
* RaftkaActor: main actor system, implemented as a finte state machine
* RaftkaClient: client (i.e. server) of the Raftka system
* RaftkaEntry: storage unit for RaftkaClient
* RaftkaGroupmember: RaftkaActor unit to be stored by RaftkaActor
* RaftkaStorage: used to store RaftkaEntries

## Usage example

// TODO: provide example of usage
