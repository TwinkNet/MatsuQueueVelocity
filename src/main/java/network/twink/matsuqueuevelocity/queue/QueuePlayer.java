package network.twink.matsuqueuevelocity.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.server.MatsuServer;
import network.twink.matsuqueuevelocity.task.NotificationTask;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class QueuePlayer {

    private final UUID uuid;
    private State queueState;
    private long stateLastUpdated;
    private int lastKnownPosInQueue = -1;
    private int cachedPunishmentSeconds = -1;
    private boolean actionBarToggle = false;
    private String cachedQueueKey;
    private String destinationServerKey;
    private ScheduledTask actionBarTask = null;
    private ScheduledTask notificationTask = null;
    private long notificationTaskId;
    private long actionBarTaskId;

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
        this.destinationServerKey = destinationServerKey;
        this.queueState = State.IDLE;
        this.stateLastUpdated = System.currentTimeMillis();
        this.cachedQueueKey = plugin.getQueue(this, false).getName();
        this.cachedPunishmentSeconds = plugin.getQueue(this, false).getPunishmentSeconds();
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
        cancelNotificationTask();
        cancelActionBarTask();
    }

    public void cancelActionBarTask() {
        this.actionBarTaskId = -1L;
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
    }

    public void cancelNotificationTask() {
        this.notificationTaskId = -1L;
        if (notificationTask != null) {
            notificationTask.cancel();
        }
    }

    public String getDestinationServerKey() {
        return destinationServerKey;
    }

    public void setDestinationServerKey(String destinationServerKey) {
        this.destinationServerKey = destinationServerKey;
    }

    public MatsuDestinationServer getDestinationMatsuServer(MatsuQueuePlugin plugin) {
        return plugin.getDestServer(getDestinationServerKey());
    }

    public Player getPlayer(MatsuQueuePlugin plugin) {
        Optional<Player> opt = plugin.getProxyServer().getPlayer(uuid);
        return opt.orElse(null);
    }

    public boolean isActionBarMessageToggled() {
        return actionBarToggle;
    }

    public void setActionBarTask(ScheduledTask actionBarTask) {
        this.actionBarTask = actionBarTask;
    }

    public void setNotificationTask(ScheduledTask notificationTask) {
        this.notificationTask = notificationTask;
    }

    public long getNotificationTaskId() {
        return notificationTaskId;
    }

    public void setActionBarTaskId(long actionBarTaskId) {
        this.actionBarTaskId = actionBarTaskId;
    }

    public long getActionBarTaskId() {
        return actionBarTaskId;
    }

    public void setNotificationTaskId(long notificationTaskId) {
        this.notificationTaskId = notificationTaskId;
    }

    public void toggleActionBar() {
        this.actionBarToggle = !this.actionBarToggle;
    }

    public void startNotificationTask(Player player, MatsuQueuePlugin plugin, boolean showEtaFirst) {
        this.cancelAnyTask();
        this.actionBarToggle = !showEtaFirst; // we need to invert it for the result to actually make sense, if it's true, it shows the status first.
        if (!player.getUniqueId().equals(uuid)) {
            throw new IllegalArgumentException("Argument Player must have same UUID as QueuePlayer.this.uuid");
        }
        final ServerQueue queue = plugin.getQueue(this, false);
        NotificationTask newNotificationTask = new NotificationTask(plugin, player, this, queue);
        this.setNotificationTaskId(newNotificationTask.getId());
        plugin.getProxyServer().getScheduler().buildTask(plugin, newNotificationTask).repeat(10L, TimeUnit.SECONDS).schedule();
    }
}
