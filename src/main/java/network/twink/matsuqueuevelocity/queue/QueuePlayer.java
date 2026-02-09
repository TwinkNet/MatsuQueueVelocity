package network.twink.matsuqueuevelocity.queue;

import java.util.UUID;

public class QueuePlayer {

    private UUID uuid;
    private State queueState;
    private long stateLastUpdated;

    public QueuePlayer(UUID uuid) {
        this.uuid = uuid;
        this.queueState = State.IDLE;
        this.stateLastUpdated = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getStateLastUpdated() {
        return stateLastUpdated;
    }

    public State getQueueState() {
        return queueState;
    }

    public void setQueueState(State queueState) {
        this.queueState = queueState;
        this.stateLastUpdated = System.currentTimeMillis();
    }

    public int getPositionInQueue() {
        return -1; // TODO
    }
}
