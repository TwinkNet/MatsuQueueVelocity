package network.twink.matsuqueuevelocity.queue;

import com.velocitypowered.api.proxy.Player;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;

import java.util.UUID;

public class QueuePlayer {

    private UUID uuid;
    private State queueState;
    private long stateLastUpdated;
    private int lastKnownPosInQueue = -1;
    int punishmentSeconds = -1;

    public QueuePlayer(MatsuQueuePlugin plugin, Player player) {
        this.uuid = player.getUniqueId();
        this.queueState = State.IDLE;
        this.stateLastUpdated = System.currentTimeMillis();
        this.punishmentSeconds = plugin.getQueue(player, false).getPunishmentSeconds();
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
        if (queueState != State.QUEUED) {
            setLastKnownPosInQueue(-1);
        }
        this.queueState = queueState;
        this.stateLastUpdated = System.currentTimeMillis();
    }

    public int getPunishmentSeconds() {
        return punishmentSeconds;
    }

    public void setPunishmentSeconds(int punishmentSeconds) {
        this.punishmentSeconds = punishmentSeconds;
    }

    public void setLastKnownPosInQueue(int lastKnownPosInQueue) {
        this.lastKnownPosInQueue = lastKnownPosInQueue;
    }

    public int getCachedPositionInQueue() {
        return lastKnownPosInQueue;
    }
}
