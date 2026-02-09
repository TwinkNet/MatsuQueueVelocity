package network.twink.matsuqueuevelocity.queue;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.slot.SlotPool;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;

public class ServerQueue {

    private String name;
    private int priority;
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

    public String getTabHeader() {
        return tabHeader;
    }

    public String getTabFooter() {
        return tabFooter;
    }

    public void setTabHeader(String tabHeader) {
        this.tabHeader = tabHeader;
    }

    public void setTabFooter(String tabFooter) {
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

    public QueuePlayer getQueuePlayer(UUID uuid) {
        for (QueuePlayer queuePlayer : queue) {
            if (queuePlayer.getUuid().equals(uuid)) return queuePlayer;
        }
        return null;
    }

    public boolean enqueue(QueuePlayer queuePlayer) {
        return queue.offer(queuePlayer);
    }

    public QueuePlayer dequeue(UUID uid) {
        for (QueuePlayer queuePlayer : queue) {
            if (queuePlayer.getUuid().equals(uid)) {
                queue.remove(queuePlayer);
                return queuePlayer;
            }
        }
        return null;
    }

    public boolean connectFirstIfPossible(MatsuQueuePlugin plugin) {
        if (plugin.isDestinationServerOnline() || queue.isEmpty() || isServerFull(plugin)) return false;
        QueuePlayer queuePlayer = queue.pop();
        Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
        if (optional.isEmpty()) return connectFirstIfPossible(plugin);
        Player player = optional.get();
        if (player.getCurrentServer().isEmpty()) return connectFirstIfPossible(plugin);
        this.joinSlotPool(plugin, queuePlayer);
        MatsuMessages matsuMessages =  plugin.getConfigurator().getMatsuMessages();
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(matsuMessages.format(matsuMessages.getConnecting(), plugin.getDestinationMatsuServer().getDisplayName(), matsuMessages.getConnecting(), -1)));
        player.createConnectionRequest(plugin.getDestinationServer()).connect().thenAccept(result -> {
            if (result.isSuccessful()) queuePlayer.setQueueState(State.PLAYING);
            else {
                plugin.getLogger().error("{} could not be added to a server slot.", player.getGameProfile().getName());
                player.disconnect(LegacyComponentSerializer.legacySection().deserialize("\247Erm"));
            }
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
        return false; // Server is full
    }

    public boolean isServerFull(MatsuQueuePlugin plugin) {
        if (!queue.isEmpty()) return true;
        boolean full = true;
        for (String prioritisedSlot : prioritisedSlots) {
            full = plugin.getSlotPool(prioritisedSlot).isFull();
        }
        return full;
    }
}
