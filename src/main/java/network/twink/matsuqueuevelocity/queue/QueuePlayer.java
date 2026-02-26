package network.twink.matsuqueuevelocity.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.server.MatsuServer;

import java.util.Optional;
import java.util.UUID;

public class QueuePlayer {

    private UUID uuid;
    private State queueState;
    private long stateLastUpdated;
    private int lastKnownPosInQueue = -1;
    private int cachedPunishmentSeconds = -1;
    private String cachedQueueKey;
    private String destinationServerKey;
    private ScheduledTask actionBarTask = null;

    public QueuePlayer(MatsuQueuePlugin plugin, Player player, String destinationServerKey, String cachedQueueKey) {
        this.uuid = player.getUniqueId();
        this.queueState = State.IDLE;
        this.stateLastUpdated = System.currentTimeMillis();
        this.cachedQueueKey = cachedQueueKey;
        this.cachedPunishmentSeconds = plugin.getQueue(this, false).getPunishmentSeconds();
        this.destinationServerKey = destinationServerKey;
    }
    public QueuePlayer(MatsuQueuePlugin plugin, Player player, MatsuServer destinationServer, String cachedQueueKey) {
        this(plugin, player, destinationServer.getVelocityName(), cachedQueueKey);
    }


    public QueuePlayer(MatsuQueuePlugin plugin, Player player, String destinationServerKey) {
        this.uuid = player.getUniqueId();
        this.queueState = State.IDLE;
        this.stateLastUpdated = System.currentTimeMillis();
        this.cachedQueueKey = plugin.getQueue(this, false).getName();
        this.cachedPunishmentSeconds = plugin.getQueue(this, false).getPunishmentSeconds();
        this.destinationServerKey = destinationServerKey;
    }
    public QueuePlayer(MatsuQueuePlugin plugin, Player player, MatsuServer destinationServer) {
        this(plugin, player, destinationServer.getVelocityName());
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

    public String getDestinationServerKey() {
        return destinationServerKey;
    }

    public void setDestinationServerKey(String destinationServerKey) {
        this.destinationServerKey = destinationServerKey;
    }

    public MatsuDestinationServer getDestinationMatsuServer(MatsuQueuePlugin plugin) {
        return plugin.getDestinationMatsuServer(getDestinationServerKey());
    }

    public Player getPlayer(MatsuQueuePlugin plugin) {
        Optional<Player> opt = plugin.getProxyServer().getPlayer(uuid);
        return opt.orElse(null);
    }

    public void setActionBarTask(ScheduledTask actionBarTask) {
        this.actionBarTask = actionBarTask;
    }
}
