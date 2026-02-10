package network.twink.matsuqueuevelocity.queue;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.slot.SlotPool;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;

public class ServerQueue {

    private final String name;
    private final int priority;
    private final String[] prioritisedSlots;

    private final LinkedBlockingDeque<QueuePlayer> queue = new  LinkedBlockingDeque<>();

    private String tabHeader;
    private String tabFooter;

    public ServerQueue(String name, int priority, String[] prioritisedSlots) {
        this.name = name;
        this.priority = priority;
        this.prioritisedSlots = prioritisedSlots;
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
        for (QueuePlayer queuePlayer : queue) {
            Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
            if (optional.isEmpty()) {
                // Super epic rare console message that should never appear but who the fuck knows because muh multithreaded proxy server
                plugin.getLogger().error("Player {} is not online, but they're still queued anyways. This message should only appear once, they'll be dequeued and sent to purgatory within 500ms", queuePlayer.getUuid());
                continue;
            }
            Player player = optional.get();
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(plugin.getConfigurator().getMatsuMessages().getPositionInQueue(plugin.getDestinationMatsuServer().getDisplayName(), count)));
            queuePlayer.setLastKnownPosInQueue(count);
            boolean flag = !plugin.isDestinationServerOnline(); // if the server IS NOT online
            String header = plugin
                    .getConfigurator()
                    .getMatsuMessages()
                    .formatTabListMessage(
                            plugin.getTabHeaderTemplateForPlayer(player),
                            !flag ?  // If the server IS online
                                    plugin.getConfigurator().getMatsuMessages().getNowQueued(plugin.getDestinationMatsuServer().getDisplayName())
                                    :
                                    plugin.getConfigurator().getMatsuMessages().getNowOffline(plugin.getDestinationMatsuServer().getDisplayName())
                            ,
                            queuePlayer.getCachedPositionInQueue()
                    );
            String footer = plugin
                    .getConfigurator()
                    .getMatsuMessages()
                    .formatTabListMessage(
                            plugin.getTabFooterTemplateForPlayer(player),
                            !flag ? // If the server IS online
                                    plugin.getConfigurator().getMatsuMessages().getNowQueued(plugin.getDestinationMatsuServer().getDisplayName())
                                    :
                                    plugin.getConfigurator().getMatsuMessages().getNowOffline(plugin.getDestinationMatsuServer().getDisplayName())
                            ,
                            queuePlayer.getCachedPositionInQueue()
                    );
            player.sendPlayerListHeaderAndFooter(LegacyComponentSerializer.legacySection().deserialize(header), LegacyComponentSerializer.legacySection().deserialize(footer));
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
     *
     * If the destination server is offline, the queue is empty, or if the server is considered full to players in this queue, it will return false
     * The method will pop the QueuePlayer and attempt to find the Player within the proxy server,
     * if the Player is not found (ex. they left the server and didn't get cleaned up),
     * the method will call itself to process the next player in queue.
     *
     * If all is well, the player will be sent to the destination server.
     * If the player joins the destination server and successfully occupies a player slot,
     * their state will be set to PLAYING.
     *
     * If anything failed, they will be kicked from the server.
     *
     * @param plugin The MatsuQueuePlugin
     * @return whether this was successful past the point of getting the Player object,
     * this will return true even if the player was kicked for other reasons.
     */
    public boolean connectFirstIfPossible(MatsuQueuePlugin plugin) {
        if (!plugin.isDestinationServerOnline() || queue.isEmpty() || isServerFull(plugin)) return false;
        QueuePlayer queuePlayer = queue.pop();
        Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
        if (optional.isEmpty()) return connectFirstIfPossible(plugin);
        Player player = optional.get();
        if (player.getCurrentServer().isEmpty()) return connectFirstIfPossible(plugin);
        final boolean flag = joinSlotPool(plugin, queuePlayer);
        MatsuMessages matsuMessages =  plugin.getConfigurator().getMatsuMessages();
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(matsuMessages.getConnecting(plugin.getDestinationMatsuServer().getDisplayName())));
        player.createConnectionRequest(plugin.getDestinationServer()).connect().thenAccept(result -> {
            if (result.isSuccessful() && flag) {
                queuePlayer.setQueueState(State.PLAYING);
            }
            else {
                plugin.getLogger().error("{} could not be connected to the destination server, or couldn't be added to a player slot.", player.getGameProfile().getName());
                player.disconnect(LegacyComponentSerializer.legacySection()
                        .deserialize(plugin.getConfigurator().getMatsuMessages().getNowOffline(plugin.getDestinationMatsuServer().getDisplayName())));
            }
        }).exceptionally(e -> {
            plugin.getLogger().error("{} could not be connected to the destination server, or couldn't be added to a player slot.", player.getGameProfile().getName());
            player.disconnect(LegacyComponentSerializer.legacySection()
                    .deserialize(plugin.getConfigurator().getMatsuMessages().getNowOffline(plugin.getDestinationMatsuServer().getDisplayName())));
            return null;
        });
        return true;
    }

    public boolean joinSlotPool(MatsuQueuePlugin plugin, QueuePlayer queuePlayer) {
        for (String prioritisedSlot : prioritisedSlots) {
            SlotPool pool = plugin.getSlotPool(prioritisedSlot);
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
            full = plugin.getSlotPool(prioritisedSlot).isFull();
        }
        return full;
    }
}
