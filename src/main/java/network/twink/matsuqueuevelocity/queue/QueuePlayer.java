package network.twink.matsuqueuevelocity.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;

import java.util.UUID;

public class QueuePlayer {

    private UUID uuid;
    private State queueState;
    private long stateLastUpdated;
    private int lastKnownPosInQueue = -1;
    private int cachedPunishmentSeconds = -1;
    private String cachedQueueKey;
    private ScheduledTask actionBarTask = null;

    public QueuePlayer(MatsuQueuePlugin plugin, Player player, String cachedQueueKey) {
        this.uuid = player.getUniqueId();
        this.queueState = State.IDLE;
        this.stateLastUpdated = System.currentTimeMillis();
        this.cachedQueueKey = cachedQueueKey;
        this.cachedPunishmentSeconds = plugin.getQueue(player, false).getPunishmentSeconds();
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

    public String getCachedQueueKey() {
        return cachedQueueKey;
    }

    public void setCachedQueueKey(String cachedQueueKey) {
        this.cachedQueueKey = cachedQueueKey;
    }

    public int getCachedPunishmentSeconds() {
        return cachedPunishmentSeconds;
    }

    public void setCachedPunishmentSeconds(int cachedPunishmentSeconds) {
        this.cachedPunishmentSeconds = cachedPunishmentSeconds;
    }

    public void setLastKnownPosInQueue(int lastKnownPosInQueue) {
        this.lastKnownPosInQueue = lastKnownPosInQueue;
    }

    public int getCachedPositionInQueue() {
        return lastKnownPosInQueue;
    }

    public void cancelAnyTask() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
    }

    public void setActionBarTask(ScheduledTask actionBarTask) {
        this.actionBarTask = actionBarTask;
    }
}
