package network.twink.matsuqueuevelocity.queue;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.slot.SlotPool;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class ServerQueue {

    private final String name;
    private final String destKey;
    private final int priority;
    private final String[] prioritisedSlots;
    private final int punishmentSeconds;
    private final long[] averageTimeBetweenJoins = new long[30];
    private int averageTimesCursor = 0;
    private long lastJoinTime = -1L;

    private final LinkedBlockingDeque<QueuePlayer> queue = new LinkedBlockingDeque<>();

    private String tabHeader;
    private String tabFooter;

    public ServerQueue(String name, String destKey, int priority, String[] prioritisedSlots, int punishmentSeconds) {
        this.name = name;
        this.destKey = destKey;
        this.priority = priority;
        this.prioritisedSlots = prioritisedSlots;
        this.punishmentSeconds = punishmentSeconds;
    }

    public long getAverageTimeBetweenJoins() {
        long sum = 0;
        int counter = 0;
        for (long averageTimeBetweenJoin : averageTimeBetweenJoins) {
            if (averageTimeBetweenJoin < 0) continue;
            counter++;
            sum += averageTimeBetweenJoin;
        }
        if (counter <= 0) {
            return -1L;
        }
        return sum / counter;
    }

    public void updateAverageTimeBetweenJoins() {
        long millis = System.currentTimeMillis();
        if (lastJoinTime == -1) {
            lastJoinTime = millis;
            return;
        }
        if (averageTimesCursor >= averageTimeBetweenJoins.length) {
            averageTimesCursor = 0;
        }
        averageTimeBetweenJoins[averageTimesCursor++] = millis - lastJoinTime;
        lastJoinTime = millis;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public String getTabHeaderTemplate() {
        return tabHeader;
    }

    public String getTabFooterTemplate() {
        return tabFooter;
    }

    public void setTabHeaderTemplate(String tabHeader) {
        this.tabHeader = tabHeader;
    }

    public void setTabFooterTemplate(String tabFooter) {
        this.tabFooter = tabFooter;
    }

    public int getPunishmentSeconds() {
        return punishmentSeconds;
    }

    public int getPositionInQueue(UUID uuid) {
        int count = 1;
        for (QueuePlayer queuePlayer : queue) {
            if (queuePlayer.getUuid().equals(uuid)) {
                return count;
            }
            count++;
        }
        return -1;
    }

    public void notifyAllQueueMembers(MatsuQueuePlugin plugin) {
        int count = 1;
        if (queue.isEmpty()) {
            this.lastJoinTime = -1L;
            // this will run every 10 sec
        }
        for (QueuePlayer queuePlayer : queue) {
            Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
            if (optional.isEmpty()) {
                // Super epic rare console message that should never appear but who the fuck knows because muh multithreaded proxy server
                plugin.getLogger().error("Player {} is not online, but they're still queued anyways. This message should only appear once, they'll be dequeued and sent to purgatory within 500ms", queuePlayer.getUuid());
                continue;
            }
            Player player = optional.get();
            MatsuDestinationServer destinationServer = queuePlayer.getDestinationMatsuServer(plugin);
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(plugin.getConfigurator().getMatsuMessages().getPositionInQueue(destinationServer.getDisplayName(), count)));
            queuePlayer.setLastKnownPosInQueue(count);
            boolean flag = !destinationServer.isOnline(); // if the server IS NOT online
            String header = plugin
                    .getConfigurator()
                    .getMatsuMessages()
                    .formatTabListMessage(
                            plugin.getTabHeaderTemplateForPlayer(queuePlayer),
                            !flag ?  // If the server IS online
                                    plugin.getConfigurator().getMatsuMessages().getNowQueued(destinationServer.getDisplayName())
                                    :
                                    plugin.getConfigurator().getMatsuMessages().getNowOffline(destinationServer.getDisplayName())
                            ,
                            queuePlayer.getCachedPositionInQueue(),
                            this.getAverageTimeBetweenJoins()
                    );
            String footer = plugin
                    .getConfigurator()
                    .getMatsuMessages()
                    .formatTabListMessage(
                            plugin.getTabFooterTemplateForPlayer(queuePlayer),
                            !flag ? // If the server IS online
                                    plugin.getConfigurator().getMatsuMessages().getNowQueued(destinationServer.getDisplayName())
                                    :
                                    plugin.getConfigurator().getMatsuMessages().getNowOffline(destinationServer.getDisplayName())
                            ,
                            queuePlayer.getCachedPositionInQueue(),
                            this.getAverageTimeBetweenJoins()
                    );
            player.sendPlayerListHeaderAndFooter(LegacyComponentSerializer.legacySection().deserialize(header), LegacyComponentSerializer.legacySection().deserialize(footer));
            final int[] counter = {0};
            final String message = plugin.getConfigurator().getMatsuMessages().getEstimatedTime(destinationServer.getDisplayName(), queuePlayer.getCachedPositionInQueue(), getAverageTimeBetweenJoins());
            queuePlayer.cancelAnyTask();
            plugin.getProxyServer().getScheduler().buildTask(plugin, (task) -> {
                if (!player.isActive() || counter[0] > 10) {
                    task.cancel();
                    return;
                }
                queuePlayer.setActionBarTask(task);
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
                counter[0]++;
            }).repeat(1L, TimeUnit.SECONDS).schedule();
            count++;
        }
    }

    public QueuePlayer getQueuePlayer(UUID uuid) {
        for (QueuePlayer queuePlayer : queue) {
            if (queuePlayer.getUuid().equals(uuid)) return queuePlayer;
        }
        return null;
    }

    public boolean enqueue(QueuePlayer queuePlayer) {
        queuePlayer.setLastKnownPosInQueue(queue.size() + 1); // this is important for the player tab list to work immediately
        queuePlayer.setCachedQueueKey(this.getName());
        return queue.offer(queuePlayer);
    }

    public QueuePlayer dequeue(UUID uid) {
        for (QueuePlayer queuePlayer : queue) {
            if (queuePlayer.getUuid().equals(uid)) {
                if (queue.remove(queuePlayer)) {
                    queuePlayer.setLastKnownPosInQueue(-1);
                    return queuePlayer;
                }
            }
        }
        return null;
    }

    /**
     * Attempts to connect the first player in queue to the destination server.
     * <p>
     * If the destination server is offline, the queue is empty, or if the server is considered full to players in this queue, it will return false
     * The method will pop the QueuePlayer and attempt to find the Player within the proxy server,
     * if the Player is not found (ex. they left the server and didn't get cleaned up),
     * the method will call itself to process the next player in queue.
     * <p>
     * If all is well, the player will be sent to the destination server.
     * If the player joins the destination server and successfully occupies a player slot,
     * their state will be set to PLAYING.
     * <p>
     * If anything failed, they will be kicked from the server.
     *
     * @param plugin The MatsuQueuePlugin
     * @return whether this was successful past the point of getting the Player object,
     * this will return true even if the player was kicked for other reasons.
     */
    public boolean connectFirstIfPossible(MatsuQueuePlugin plugin) {
        if (queue.isEmpty() || isServerFull(plugin)) return false;
        if (!queue.element().getDestinationMatsuServer(plugin).isOnline()) return false;
        QueuePlayer queuePlayer = queue.pop();
        MatsuDestinationServer dest = queuePlayer.getDestinationMatsuServer(plugin);
        if (!dest.getVelocityName().equals(destKey)) return false;
        Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
        if (optional.isEmpty()) return connectFirstIfPossible(plugin);
        Player player = optional.get();
        if (player.getCurrentServer().isEmpty()) return connectFirstIfPossible(plugin);
        final boolean flag = joinSlotPool(plugin, queuePlayer);
        MatsuMessages matsuMessages = plugin.getConfigurator().getMatsuMessages();
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(matsuMessages.getConnecting(dest.getDisplayName())));
        this.updateAverageTimeBetweenJoins();
        player.createConnectionRequest(dest.getServer(plugin)).connect().thenAccept(result -> {
            if (result.isSuccessful() && flag) {
                queuePlayer.setQueueState(State.PLAYING);
                queuePlayer.cancelAnyTask();
                player.sendActionBar(Component.empty());
            } else {
                plugin.getLogger().error("{} could not be connected to the destination server, or couldn't be added to a player slot.", player.getGameProfile().getName());
                player.disconnect(LegacyComponentSerializer.legacySection()
                        .deserialize(plugin.getConfigurator().getMatsuMessages().getNowOffline(dest.getDisplayName())));
            }
        }).exceptionally(e -> {
            plugin.getLogger().error("{} could not be connected to the destination server, or couldn't be added to a player slot.", player.getGameProfile().getName());
            player.disconnect(LegacyComponentSerializer.legacySection()
                    .deserialize(plugin.getConfigurator().getMatsuMessages().getNowOffline(dest.getDisplayName())));
            return null;
        });
        return true;
    }

    public boolean joinSlotPool(MatsuQueuePlugin plugin, QueuePlayer queuePlayer) {
        for (String prioritisedSlot : prioritisedSlots) {
            SlotPool pool = plugin.getSlotPool(destKey, prioritisedSlot);
            if (!pool.isFull()) {
                pool.addPlayer(queuePlayer);
                return true;
            }
        }
        return false; // ERM! Aktshully... the server is full!
    }

    public boolean arePlayersQueued() {
        return !queue.isEmpty();
    }

    public boolean isServerFull(MatsuQueuePlugin plugin) {
        boolean full = true;
        for (String prioritisedSlot : prioritisedSlots) {
            full = plugin.getSlotPool(destKey, prioritisedSlot).isFull();
        }
        return full;
    }
}
