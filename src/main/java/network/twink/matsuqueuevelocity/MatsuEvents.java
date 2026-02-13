package network.twink.matsuqueuevelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
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
        String queueServerOffline = getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getQueueMatsuServer().getDisplayName());
        String destServerOffline = getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getDestinationMatsuServer().getDisplayName());
        if (queuePlayer == null) {
            queuePlayer = new QueuePlayer(player.getUniqueId());
        }
        boolean needToQueue = !getPlugin().isDestinationServerOnline() || getPlugin().isDestinationServerFull(player);
        if (needToQueue) {
            if (!getPlugin().isQueueServerOnline()) {
                event.getPlayer().disconnect(LegacyComponentSerializer.legacySection().deserialize(queueServerOffline));
                return;
            }
            if (getPlugin().joinQueue(player, queuePlayer)) {
                queuePlayer.setQueueState(State.QUEUED);
                event.setInitialServer(getPlugin().getQueueServer());
            } else {
                event.getPlayer().disconnect(LegacyComponentSerializer.legacySection().deserialize(queueServerOffline));
                // if this happens it's 99% because the server admin fucked up and removed the default queue from the config
                // we will not let every player that tries to join know though, we'll just pretend the queue server is offline.
                // the server admin will figure it out eventually when they see all of our fuckass console spam.
                return;
            }
        } else {
            boolean joined = getPlugin().joinSlotPool(player, queuePlayer);
            if (!joined) {
                event.getPlayer().disconnect(LegacyComponentSerializer.legacySection().deserialize(destServerOffline));
                // if this happens it's 99% because the server admin fucked up and removed the default queue from the config
                // we will not let every player that tries to join know though, we'll just pretend the destination server is offline.
                // the server admin will figure it out eventually when they see all of our fuckass console spam.
                return;
            }
            if (!getPlugin().isDestinationServerOnline() || (queuePlayer.getQueueState() == State.LEFT && System.currentTimeMillis() - queuePlayer.getStateLastUpdated() < getPlugin().getGlobalPunishmentSeconds() * 1000L)) {
                if (!getPlugin().isQueueServerOnline()) {
                    event.getPlayer().disconnect(LegacyComponentSerializer.legacySection().deserialize(queueServerOffline));
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
                        boolean flag = !getPlugin().isDestinationServerOnline(); // if the server IS NOT online
                        e.getPlayer().sendMessage(LegacyComponentSerializer.legacySection().deserialize(flag ?
                                matsuMessages.getNowOffline(getPlugin().getDestinationMatsuServer().getDisplayName()) :
                                matsuMessages.getNowQueued(getPlugin().getDestinationMatsuServer().getDisplayName())
                        ));
                        String header = getPlugin()
                                .getConfigurator()
                                .getMatsuMessages()
                                .formatTabListMessage(
                                        getPlugin().getTabHeaderTemplateForPlayer(e.getPlayer()),
                                        !flag ?  // If the server IS online
                                                getPlugin().getConfigurator().getMatsuMessages().getNowQueued(getPlugin().getDestinationMatsuServer().getDisplayName())
                                                :
                                                getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getDestinationMatsuServer().getDisplayName())
                                        ,
                                        queuePlayer.getCachedPositionInQueue()
                                );
                        String footer = getPlugin()
                                .getConfigurator()
                                .getMatsuMessages()
                                .formatTabListMessage(
                                        getPlugin().getTabFooterTemplateForPlayer(e.getPlayer()),
                                        !flag ? // If the server IS online
                                                getPlugin().getConfigurator().getMatsuMessages().getNowQueued(getPlugin().getDestinationMatsuServer().getDisplayName())
                                                :
                                                getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getDestinationMatsuServer().getDisplayName())
                                        ,
                                        queuePlayer.getCachedPositionInQueue()
                                );
                        e.getPlayer().sendPlayerListHeaderAndFooter(LegacyComponentSerializer.legacySection().deserialize(header), LegacyComponentSerializer.legacySection().deserialize(footer));
                    }
                    case PENDING -> {
                        boolean flag = !getPlugin().isDestinationServerOnline(); // if the server IS NOT online
                        MatsuMessages matsuMessages = getPlugin().getConfigurator().getMatsuMessages();
                        e.getPlayer().sendMessage(LegacyComponentSerializer.legacySection().deserialize(flag ?
                                matsuMessages.getNowOffline(getPlugin().getDestinationMatsuServer().getDisplayName()) :
                                matsuMessages.getPendingConnection(getPlugin().getDestinationMatsuServer().getDisplayName())
                        ));
                        String header = getPlugin()
                                .getConfigurator()
                                .getMatsuMessages()
                                .formatTabListMessage(
                                        getPlugin().getTabHeaderTemplateForPlayer(e.getPlayer()),
                                        !flag ? // If the server IS online
                                                getPlugin().getConfigurator().getMatsuMessages().getPendingConnection(getPlugin().getDestinationMatsuServer().getDisplayName())
                                                :
                                                getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getDestinationMatsuServer().getDisplayName())
                                        ,
                                        queuePlayer.getCachedPositionInQueue()
                                );
                        String footer = getPlugin()
                                .getConfigurator()
                                .getMatsuMessages()
                                .formatTabListMessage(
                                        getPlugin().getTabFooterTemplateForPlayer(e.getPlayer()),
                                        !flag ? // If the server IS online
                                                getPlugin().getConfigurator().getMatsuMessages().getPendingConnection(getPlugin().getDestinationMatsuServer().getDisplayName())
                                                :
                                                getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getDestinationMatsuServer().getDisplayName())
                                        ,
                                        queuePlayer.getCachedPositionInQueue()
                                );
                        e.getPlayer().sendPlayerListHeaderAndFooter(LegacyComponentSerializer.legacySection().deserialize(header), LegacyComponentSerializer.legacySection().deserialize(footer));
                    }
                }
            }
        }
    }

    @Subscribe
    public void onKick(KickedFromServerEvent e) {
        // Sometimes velocity doesn't respect that we don't want to connect players to a fallback server on kick
        // even when we have failover-on-unexpected-server-disconnect set to false in the config.
        // we will just make sure they get kicked no matter what.
        e.getPlayer().disconnect(e.getServerKickReason().orElse(LegacyComponentSerializer.legacySection().deserialize(
                getPlugin().getConfigurator().getMatsuMessages().getNowOffline(getPlugin().getQueueMatsuServer().getDisplayName())
        )));
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
