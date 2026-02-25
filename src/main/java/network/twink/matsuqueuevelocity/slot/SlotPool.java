package network.twink.matsuqueuevelocity.slot;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SlotPool {

    private String name;
    private int capacity;
    private final ConcurrentHashMap<UUID, QueuePlayer> fill;

    public SlotPool(String name, int capacity, QueuePlayer[] fill) {
        this.name = name;
        this.capacity = capacity;
        this.fill = new ConcurrentHashMap<>(capacity);
        for (QueuePlayer queuePlayer : fill) {
            this.fill.put(queuePlayer.getUuid(), queuePlayer);
        }
    }

    public SlotPool(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        this.fill = new ConcurrentHashMap<>();
    }

    public void notifyAnyPending(MatsuQueuePlugin plugin) {
        fill.keySet().forEach(uuid -> {
            QueuePlayer queuePlayer = fill.get(uuid);
            if (queuePlayer.getQueueState() == State.PENDING) {

                Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
                if (optional.isEmpty()) return;
                Player player = optional.get();
                if (player.getCurrentServer().isEmpty()) return;

                MatsuMessages matsuMessages = plugin.getConfigurator().getMatsuMessages();
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        matsuMessages.getPendingConnection(plugin.getDestinationMatsuServer().getDisplayName())
                ));
            } else if (queuePlayer.getQueueState() == State.PLAYING) {
                Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
                if (optional.isEmpty()) return;
                Player player = optional.get();
                Optional<ServerConnection> connection = player.getCurrentServer();
                if (connection.isEmpty()) return;
                if (connection.get().getServerInfo().getName().equals(plugin.getQueueServer().getServerInfo().getName())) {
                    player.disconnect(LegacyComponentSerializer.legacySection()
                            .deserialize(plugin.getConfigurator().getMatsuMessages().getNowOffline(plugin.getDestinationMatsuServer().getDisplayName())));
                }
            }
        });
    }

    public void connectAnyPending(MatsuQueuePlugin plugin) {
        if (!plugin.isDestinationServerOnline()) return;
        fill.keySet().forEach((uuid) -> {
            QueuePlayer queuePlayer = fill.get(uuid);
            if (queuePlayer.getQueueState() == State.PENDING && System.currentTimeMillis() - queuePlayer.getStateLastUpdated() > queuePlayer.getCachedPunishmentSeconds() * 1000L) {
                Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
                if (optional.isEmpty()) return;
                Player player = optional.get();
                if (player.getCurrentServer().isEmpty()) return;
                MatsuMessages matsuMessages = plugin.getConfigurator().getMatsuMessages();
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(matsuMessages.getConnecting(plugin.getDestinationMatsuServer().getDisplayName())));
                player.createConnectionRequest(plugin.getDestinationServer()).connect().thenAccept(result -> {
                    if (result.isSuccessful()) {
                        queuePlayer.setQueueState(State.PLAYING);
                        queuePlayer.cancelAnyTask();
                        player.sendActionBar(Component.empty());
                    }
                    else {
                        plugin.getLogger().error("{} could not be connected to the main server.", player.getGameProfile().getName());
                        player.disconnect(LegacyComponentSerializer.legacySection()
                                .deserialize(plugin.getConfigurator().getMatsuMessages().getNowOffline(plugin.getDestinationMatsuServer().getDisplayName())));
                    }
                });
            }
        });
    }

    public String getName() {
        return name;
    }
    public int getCapacity() {
        return capacity;
    }
    public int getPlayerCount() {
        return fill.size();
    }
    public boolean isFull() {
        return fill.size() >= capacity;
    }
    public void addPlayer(QueuePlayer player) {
        fill.put(player.getUuid(), player);
    }
    public void removePlayer(QueuePlayer player) {
        fill.remove(player.getUuid());
    }
    public QueuePlayer getAndRemovePlayer(UUID player) {
        QueuePlayer queuePlayer = fill.get(player);
        fill.remove(player);
        return queuePlayer;
    }
    public QueuePlayer getQueuePlayer(UUID uuid) {
        return fill.get(uuid);
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
