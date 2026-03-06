package network.twink.matsuqueuevelocity.slot;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.ServerQueue;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    @Deprecated
    private void sendPendingUpdate(MatsuQueuePlugin plugin, Player player, QueuePlayer qp) {
        plugin.getNotificationManager().sendQueueUI(player, qp, null, false);
    }

    @Deprecated
    public void notifyAnyPending(MatsuQueuePlugin plugin) {
        fill.keySet().forEach(uuid -> {
            QueuePlayer queuePlayer = fill.get(uuid);
            if (queuePlayer.getQueueState() == State.PENDING) {
                Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
                if (optional.isEmpty()) return;
                Player player = optional.get();
                if (player.getCurrentServer().isEmpty()) return;
                sendPendingUpdate(plugin, player, queuePlayer);
            } else if (queuePlayer.getQueueState() == State.PLAYING) {
                Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
                if (optional.isEmpty()) return;
                Player player = optional.get();
                Optional<ServerConnection> connection = player.getCurrentServer();
                if (connection.isEmpty()) return;
                if (connection.get().getServerInfo().getName().equals(plugin.getQueueMatsuServer().getVelocityName())) {
                    MatsuDestinationServer destinationServer = queuePlayer.getDestinationMatsuServer(plugin);
                    player.disconnect(LegacyComponentSerializer.legacySection()
                            .deserialize(plugin.getConfigurator().getMatsuMessages().getNowOffline(destinationServer.getDisplayName())));
                }
            }
        });
    }

    public void connectAnyPending(MatsuDestinationServer destinationServer, MatsuQueuePlugin plugin) {
        if (!destinationServer.isOnline()) return;
        fill.keySet().forEach((uuid) -> {
            QueuePlayer queuePlayer = fill.get(uuid);
            if (queuePlayer.getQueueState() == State.PENDING && System.currentTimeMillis() - queuePlayer.getStateLastUpdated() > queuePlayer.getCachedPunishmentSeconds() * 1000L) {
                Optional<Player> optional = plugin.getProxyServer().getPlayer(queuePlayer.getUuid());
                if (optional.isEmpty()) return;
                Player player = optional.get();
                if (player.getCurrentServer().isEmpty()) return;
                MatsuMessages matsuMessages = plugin.getConfigurator().getMatsuMessages();
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(matsuMessages.getConnecting(destinationServer.getDisplayName())));
                player.createConnectionRequest(destinationServer.getServer(plugin)).connect().thenAccept(result -> {
                    if (result.isSuccessful()) {
                        queuePlayer.setQueueState(State.PLAYING);
                        queuePlayer.cancelAnyTask();
                        player.sendActionBar(Component.empty());
                    } else {
                        plugin.getLogger().error("{} could not be connected to the main server.", player.getGameProfile().getName());
                        player.disconnect(LegacyComponentSerializer.legacySection()
                                .deserialize(plugin.getConfigurator().getMatsuMessages().getNowOffline(destinationServer.getDisplayName())));
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
