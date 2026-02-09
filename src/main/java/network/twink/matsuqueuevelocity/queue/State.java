package network.twink.matsuqueuevelocity.queue;

public enum State {

    IDLE, // Just joined, not occupying a server slot, not in queue.
    QUEUED, // Occupying a position in queue
    PENDING, // Occupying a server slot, but waiting in queue server for timeout to expire.
    PLAYING, // Occupying a server slot, connected to the main server
    LEFT; // Not occupying a slot nor a queue position, in purgatory while timeout expires unless they join back.
}
