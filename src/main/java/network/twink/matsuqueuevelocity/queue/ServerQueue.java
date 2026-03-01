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

public class ServerQueue {

    private final String name;
    private final int priority;
    private final String[] prioritisedSlots;
    private final int punishmentSeconds;

    private final long[] averageTimeBetweenJoins = new long[30];
    private int averageTimesCursor = 0;
    private long lastJoinTime = -1L;

    private final LinkedBlockingDeque<QueuePlayer> queue = new LinkedBlockingDeque<>();
    private String tabHeader, tabFooter;

    public ServerQueue(String name, int priority, String[] prioritisedSlots, int punishmentSeconds) {
        this.name = name;
        this.priority = priority;
        this.prioritisedSlots = prioritisedSlots;
        this.punishmentSeconds = punishmentSeconds;
        java.util.Arrays.fill(averageTimeBetweenJoins, -1L);
    }

    public boolean enqueue(QueuePlayer qp) {
        qp.setLastKnownPosInQueue(queue.size() + 1);
        qp.setCachedQueueKey(this.name);
        return queue.offer(qp);
    }

    public QueuePlayer dequeue(UUID uuid) {
        for (QueuePlayer qp : queue) {
            if (qp.getUuid().equals(uuid)) {
                queue.remove(qp);
                qp.setLastKnownPosInQueue(-1);
                return qp;
            }
        }
        return null;
    }

    // notifications

    public void notifyAllQueueMembers(MatsuQueuePlugin plugin) {
        if (queue.isEmpty()) {
            this.lastJoinTime = -1L;
            return;
        }

        int count = 1;
        for (QueuePlayer qp : queue) {
            Optional<Player> playerOpt = plugin.getProxyServer().getPlayer(qp.getUuid());
            if (playerOpt.isEmpty()) {
                plugin.getLogger().warn("Player {} is queued but not online. Purgatory will handle them shortly.", qp.getUuid());
                count++;
                continue;
            }

            sendQueueUpdate(plugin, playerOpt.get(), qp, count++);
        }
    }

    private void sendQueueUpdate(MatsuQueuePlugin plugin, Player player, QueuePlayer qp, int pos) {
        qp.setLastKnownPosInQueue(pos);
        plugin.getNotificationManager().sendQueueUI(player, qp, this, false);
    }

    // connection handling

    public boolean connectFirstIfPossible(MatsuQueuePlugin plugin) {
        if (queue.isEmpty()) return false; // no-one is here to connect
        while (!queue.isEmpty()) {
            QueuePlayer qp = queue.peek();
            MatsuDestinationServer dest = qp.getDestinationMatsuServer(plugin);
            if (!dest.isOnline() || isServerFull(qp, plugin)) return false;
            queue.pop();
            Optional<Player> playerOpt = plugin.getProxyServer().getPlayer(qp.getUuid());
            if (playerOpt.isPresent() && playerOpt.get().getCurrentServer().isPresent()) {
                connectToDestination(plugin, playerOpt.get(), qp, dest);
                return true;
            }
        }
        return false;
    }

    private void connectToDestination(MatsuQueuePlugin plugin, Player player, QueuePlayer qp, MatsuDestinationServer dest) {
        MatsuMessages msgs = plugin.getConfigurator().getMatsuMessages();
        boolean slotReserved = joinSlotPool(plugin, qp);

        player.sendMessage(msg(msgs.getConnecting(dest.getDisplayName())));
        updateAverageTimeBetweenJoins();

        player.createConnectionRequest(dest.getServer(plugin)).connect().thenAccept(result -> {
            if (result.isSuccessful() && slotReserved) {
                qp.setQueueState(State.PLAYING);
                qp.cancelAnyTask();
                player.sendActionBar(Component.empty());
            } else {
                handleConnectionFailure(plugin, player, dest);
            }
        }).exceptionally(e -> {
            handleConnectionFailure(plugin, player, dest);
            return null;
        });
    }

    private void handleConnectionFailure(MatsuQueuePlugin plugin, Player player, MatsuDestinationServer dest) {
        plugin.getLogger().error("Failed to connect {} to {}", player.getUsername(), dest.getDisplayName());
        player.disconnect(msg(plugin.getConfigurator().getMatsuMessages().getNowOffline(dest.getDisplayName())));
    }

    // slots

    public boolean joinSlotPool(MatsuQueuePlugin plugin, QueuePlayer qp) {
        MatsuDestinationServer dest = qp.getDestinationMatsuServer(plugin);
        for (String slotName : prioritisedSlots) {
            SlotPool pool = dest.getSlotMap().get(slotName);
            if (pool != null && !pool.isFull()) {
                pool.addPlayer(qp);
                return true;
            }
        }
        return false;
    }

    public boolean isServerFull(QueuePlayer player, MatsuQueuePlugin plugin) {
        MatsuDestinationServer dest = player.getDestinationMatsuServer(plugin);
        for (String slotName : prioritisedSlots) {
            SlotPool pool = dest.getSlotMap().get(slotName);
            if (pool != null && !pool.isFull()) return false;
        }
        return true;
    }

    // stats

    public void updateAverageTimeBetweenJoins() {
        long now = System.currentTimeMillis();
        if (lastJoinTime != -1) {
            averageTimeBetweenJoins[averageTimesCursor] = now - lastJoinTime;
            averageTimesCursor = (averageTimesCursor + 1) % averageTimeBetweenJoins.length;
        }
        lastJoinTime = now;
    }

    public long getAverageTimeBetweenJoins() {
        long sum = 0;
        int count = 0;
        for (long val : averageTimeBetweenJoins) {
            if (val != -1) {
                sum += val;
                count++;
            }
        }
        return (count == 0) ? -1L : sum / count;
    }

    private Component msg(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    public String getName() { return name; }
    public int getPriority() { return priority; }
    public String getTabHeaderTemplate() { return tabHeader; }
    public String getTabFooterTemplate() { return tabFooter; }
    public void setTabHeaderTemplate(String s) { this.tabHeader = s; }
    public void setTabFooterTemplate(String s) { this.tabFooter = s; }
    public int getPunishmentSeconds() { return punishmentSeconds; }
    public boolean arePlayersQueued() { return !queue.isEmpty(); }
    public int getQueueSize() { return queue.size(); }
    public QueuePlayer getQueuePlayer(UUID uuid) {
        return queue.stream().filter(p -> p.getUuid().equals(uuid)).findFirst().orElse(null);
    }
}