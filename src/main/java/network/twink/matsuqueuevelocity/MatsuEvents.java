package network.twink.matsuqueuevelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

import java.util.UUID;

public class MatsuEvents {

    private final MatsuQueuePlugin plugin;

    public MatsuEvents(MatsuQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onProxyJoin(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        QueuePlayer queuePlayer = getPlugin().popQueuePlayer(player.getUniqueId());
        if (queuePlayer == null) {
            queuePlayer = new QueuePlayer(player.getUniqueId());
        }
        boolean needToQueue = getPlugin().isDestinationServerFull(player);
        if (needToQueue) {
            if (!getPlugin().isQueueServerOnline()) {
                event.getPlayer().disconnect(LegacyComponentSerializer.legacySection().deserialize(getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getQueueMatsuServer().getDisplayName())));
                return;
            }
            getPlugin().joinQueue(player, queuePlayer);
            queuePlayer.setQueueState(State.QUEUED);
            event.setInitialServer(getPlugin().getQueueServer());
        } else {
            boolean joined = getPlugin().joinSlotPool(player, queuePlayer);
            if (!joined) {
                plugin.getLogger().error("{} could not be added to a server slot.", player.getGameProfile().getName());
                player.disconnect(LegacyComponentSerializer.legacySection().deserialize("\2476A severe error occurred while connecting."));
                return;
            }
            if (!getPlugin().isDestinationServerOnline() || (queuePlayer.getQueueState() == State.LEFT && System.currentTimeMillis() - queuePlayer.getStateLastUpdated() < getPlugin().getGlobalPunishmentSeconds() * 1000L)) {
                if (!getPlugin().isQueueServerOnline()) {
                    event.getPlayer().disconnect(LegacyComponentSerializer.legacySection().deserialize(getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getQueueMatsuServer().getDisplayName())));
                    return;
                }
                queuePlayer.setQueueState(State.PENDING);
                event.setInitialServer(getPlugin().getQueueServer());
                return;
            }
            queuePlayer.setQueueState(State.PLAYING);
            event.setInitialServer(getPlugin().getDestinationServer());
        }
    }

    @Subscribe
    public void onJoinWorld(ServerPostConnectEvent e) {
        if (e.getPlayer().getCurrentServer().isPresent()) {
            ServerConnection serverConnection = e.getPlayer().getCurrentServer().get();
            if (serverConnection.getServerInfo().getName().equals(getPlugin().getQueueServer().getServerInfo().getName())) {
                QueuePlayer queuePlayer = getPlugin().findQueuePlayer(e.getPlayer().getUniqueId());
                switch (queuePlayer.getQueueState()) {
                    case QUEUED -> {
                        MatsuMessages matsuMessages = getPlugin().getConfigurator().getMatsuMessages();
                        e.getPlayer().sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                                matsuMessages.getNowQueued(getPlugin().getDestinationMatsuServer().getDisplayName())
                        ));
                    }
                    case PENDING -> {
                        boolean flag = !getPlugin().isDestinationServerOnline();
                        MatsuMessages matsuMessages = getPlugin().getConfigurator().getMatsuMessages();
                        e.getPlayer().sendMessage(LegacyComponentSerializer.legacySection().deserialize(flag ?
                                matsuMessages.getNowOffline(getPlugin().getDestinationMatsuServer().getDisplayName()) :
                                matsuMessages.getPendingConnection(getPlugin().getDestinationMatsuServer().getDisplayName())
                        ));
                    }
                }
            }
        }
    }

    @Subscribe
    public void onLeave(DisconnectEvent dc) {
        UUID id = dc.getPlayer().getUniqueId();
        QueuePlayer queuePlayer = plugin.popQueuePlayer(id);
        if (queuePlayer != null) {
            queuePlayer.setQueueState(State.LEFT);
            plugin.purgatory.offer(queuePlayer);
        }
    }


    public MatsuQueuePlugin getPlugin() {
        return plugin;
    }
}
