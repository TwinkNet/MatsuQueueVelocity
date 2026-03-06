package network.twink.matsuqueuevelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

public class MatsuEventHandler {

    private final MatsuQueuePlugin plugin;

    public MatsuEventHandler(MatsuQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onProxyJoin(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        MatsuMessages messages = plugin.getConfigurator().getMatsuMessages();
        String queueOfflineText = messages.getNowOffline(plugin.getQueueMatsuServer().getDisplayName());
        // do we have a valid destination server? if not, something is probably wrong in velocity.toml, and it somehow got around our checks in MatsuConfigurator.
        RegisteredServer destination = event.getInitialServer().orElse(null);
        if (destination == null) {
            player.disconnect(MatsuQueuePlugin.msg(queueOfflineText));
            return;
        }

        MatsuDestinationServer matsuDest = plugin.getDestServer(destination.getServerInfo().getName());
        QueuePlayer queuePlayer = plugin.popQueuePlayer(player.getUniqueId());
        if (queuePlayer == null) {
            queuePlayer = new QueuePlayer(plugin, player, matsuDest);
        }
        queuePlayer.setDestinationServerKey(destination.getServerInfo().getName());
        String destinationOfflineText = messages.getNowOffline(matsuDest.getDisplayName());
        // do we need to queue the player?
        boolean needToQueue = !matsuDest.isOnline() || plugin.isDestinationServerFull(queuePlayer);
        boolean isPunished = queuePlayer.getQueueState() == State.LEFT &&
                (System.currentTimeMillis() - queuePlayer.getStateLastUpdated() < queuePlayer.getCachedPunishmentSeconds() * 1000L);

        if (needToQueue) {
            handleQueueEntry(event, queuePlayer, queueOfflineText);
        } else if (isPunished) {
            handlePendingEntry(event, queuePlayer, queueOfflineText);
        } else {
            handleDirectEntry(event, queuePlayer, matsuDest, destinationOfflineText);
        }
    }

    private void handleQueueEntry(PlayerChooseInitialServerEvent event, QueuePlayer qp, String offlineMsg) {
        if (!plugin.getQueueMatsuServer().isOnline() || !plugin.joinQueue(event.getPlayer(), qp, false)) {
            event.getPlayer().disconnect(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        qp.setQueueState(State.QUEUED);
        qp.startNotificationTask(event.getPlayer(), plugin, true);
        event.setInitialServer(plugin.getQueueMatsuServer().getServer(plugin));
    }

    private void handlePendingEntry(PlayerChooseInitialServerEvent event, QueuePlayer qp, String offlineMsg) {
        if (!plugin.getQueueMatsuServer().isOnline() || !plugin.joinSlotPool(qp, false)) {
            event.getPlayer().disconnect(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        qp.setQueueState(State.PENDING);
        qp.startNotificationTask(event.getPlayer(), plugin, true);
        event.setInitialServer(plugin.getQueueMatsuServer().getServer(plugin));
    }

    private void handleDirectEntry(PlayerChooseInitialServerEvent event, QueuePlayer qp, MatsuDestinationServer dest, String offlineMsg) {
        if (!dest.isOnline()) {
            event.getPlayer().disconnect(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        if (!plugin.joinSlotPool(qp, false)) {
            event.getPlayer().disconnect(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }

        qp.setQueueState(State.PLAYING);
        event.setInitialServer(dest.getServer(plugin));
    }

    @Subscribe
    public void onJoinWorld(ServerPostConnectEvent e) {
        e.getPlayer().getCurrentServer().ifPresent(server -> {
            // send tablist and actionbar to players in the queue server
            if (!server.getServerInfo().getName().equals(plugin.getQueueMatsuServer().getServer(plugin).getServerInfo().getName())) {
                return;
            }

            QueuePlayer qp = plugin.findQueuePlayer(e.getPlayer().getUniqueId());
            if (qp == null) {
                e.getPlayer().disconnect(MatsuQueuePlugin.msg("\2476You have been disconnected from the server"));
                return;
            }

            State state = qp.getQueueState();
            if (state == State.QUEUED || state == State.PENDING) {
                plugin.getNotificationManager().sendQueueUI(e.getPlayer(), qp, null, true);
            }
        });
    }

    @Subscribe
    public void onKick(KickedFromServerEvent e) {
        String reason = plugin.getConfigurator().getMatsuMessages().getNowOffline(plugin.getQueueMatsuServer().getDisplayName());
        e.getPlayer().disconnect(e.getServerKickReason().orElse(MatsuQueuePlugin.msg(reason)));
    }

    @Subscribe
    public void onLeave(DisconnectEvent dc) {
        QueuePlayer qp = plugin.popQueuePlayer(dc.getPlayer().getUniqueId());
        if (qp != null) {
            qp.setQueueState(State.LEFT);
            plugin.purgatory.offer(qp);
        }
    }

}