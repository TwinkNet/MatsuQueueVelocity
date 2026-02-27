package network.twink.matsuqueuevelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        event.setInitialServer(plugin.getQueueMatsuServer().getServer(plugin));
    }

    private void handlePendingEntry(PlayerChooseInitialServerEvent event, QueuePlayer qp, String offlineMsg) {
        if (!plugin.getQueueMatsuServer().isOnline() || !plugin.joinSlotPool(qp, false)) {
            event.getPlayer().disconnect(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        qp.setQueueState(State.PENDING);
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
                updateQueueUI(e.getPlayer(), qp, state);
            }
        });
    }

    private void updateQueueUI(Player player, QueuePlayer qp, State state) {
        MatsuMessages msgs = plugin.getConfigurator().getMatsuMessages();
        MatsuDestinationServer dest = qp.getDestinationMatsuServer(plugin);
        boolean isOnline = dest.isOnline();

        // set messages depending on State
        String status;
        String actionMsg;
        if (state == State.QUEUED) {
            status = isOnline ? msgs.getNowQueued(dest.getDisplayName()) : msgs.getNowOffline(dest.getDisplayName());
            actionMsg = msgs.getEstimatedTime(dest.getDisplayName(), qp.getCachedPositionInQueue(), plugin.getQueue(qp, false).getAverageTimeBetweenJoins());
        } else { // PENDING
            status = isOnline ? msgs.getWaitingConnection(dest.getDisplayName()) : msgs.getNowOffline(dest.getDisplayName());
            actionMsg = msgs.getEstimatedTime(dest.getDisplayName(), qp.getCachedPositionInQueue(), qp.getCachedPunishmentSeconds(), false);
        }

        player.sendMessage(MatsuQueuePlugin.msg(status));
        long eta = (state == State.QUEUED) ? plugin.getQueue(qp, false).getAverageTimeBetweenJoins() : qp.getCachedPunishmentSeconds();
        String header = msgs.formatTabListMessage(plugin.getTabHeaderTemplateForPlayer(qp), status, qp.getCachedPositionInQueue(), eta, state == State.QUEUED);
        String footer = msgs.formatTabListMessage(plugin.getTabFooterTemplateForPlayer(qp), status, qp.getCachedPositionInQueue(), eta, state == State.QUEUED);

        player.sendPlayerListHeaderAndFooter(MatsuQueuePlugin.msg(header), MatsuQueuePlugin.msg(footer));

        // update action bar eta
        qp.cancelAnyTask();
        plugin.getProxyServer().getScheduler().buildTask(plugin, (task) -> {
            if (!player.isActive()) {
                task.cancel();
                return;
            }
            qp.setActionBarTask(task);
            player.sendActionBar(MatsuQueuePlugin.msg(actionMsg));
        }).repeat(1L, TimeUnit.SECONDS).schedule();
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