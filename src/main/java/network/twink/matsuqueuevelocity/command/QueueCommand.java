package network.twink.matsuqueuevelocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.util.MatsuMessages;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class QueueCommand implements SimpleCommand {

    private final MatsuQueuePlugin plugin;

    public QueueCommand(MatsuQueuePlugin matsuQueuePlugin) {
        this.plugin = matsuQueuePlugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.arguments().length == 0) {
            for (String destinationServerKey : plugin.getDestinationServerKeys()) {
                MatsuDestinationServer server = plugin.getDestServer(destinationServerKey);
                StringBuilder builder = new StringBuilder();
                builder.append("\2476").append(server.getVelocityName()).append(" queue lengths:");
                server.getQueueMap().forEach((key, value) -> {
                    builder.append("\n\2476").append(key).append(" queue: \247l").append(value.getQueueSize());
                });
                invocation.source().sendMessage(MatsuQueuePlugin.msg(builder.toString()));
            }
        } else if (invocation.arguments().length == 1) {
            if (invocation.source() instanceof Player player) {
                QueuePlayer queuePlayer = plugin.findQueuePlayer(player.getUniqueId());
                if (queuePlayer != null) {
                    String destinationServerKey = invocation.arguments()[0];
                    MatsuDestinationServer destinationServer = plugin.getDestServer(destinationServerKey);
                    if (destinationServer != null) {
                        Optional<ServerConnection> connection = player.getCurrentServer();
                        MatsuMessages messages = plugin.getConfigurator().getMatsuMessages();
                        String queueOfflineText = messages.getNowOffline(plugin.getQueueMatsuServer().getDisplayName());
                        String destinationOfflineText = messages.getNowOffline(destinationServer.getDisplayName());
                        if (connection.isEmpty()) {
                            player.sendMessage(MatsuQueuePlugin.msg(messages.getInternalError()));
                            return;
                        }
                        if (queuePlayer.getQueueState() != State.PLAYING && queuePlayer.getDestinationServerKey().equalsIgnoreCase(destinationServerKey)) {
                            player.sendMessage(MatsuQueuePlugin.msg(messages.getAlreadyQueuedCommandConfirmation(destinationServer.getDisplayName())));
                            // already queued for that server
                            return;
                        }
                        if (connection.get().getServerInfo().getName().equalsIgnoreCase(destinationServerKey)) {
                            player.sendMessage(MatsuQueuePlugin.msg(messages.getAlreadyConnectedCommandConfirmation(destinationServer.getDisplayName())));
                            // already connected
                            return;
                        }
                        // ok everything is good lets do our thing
                        queuePlayer.setDestinationServerKey(destinationServerKey);
                        queuePlayer.cancelAnyTask();
                        player.sendActionBar(Component.empty());
                        boolean needToQueue = !destinationServer.isOnline() || plugin.isDestinationServerFull(queuePlayer);
                        boolean isPunished = (System.currentTimeMillis() - queuePlayer.getStateLastUpdated() < queuePlayer.getCachedPunishmentSeconds() * 1000L);
                        boolean sendToQueue = !connection.get().getServerInfo().getName().equalsIgnoreCase(plugin.getQueueMatsuServer().getVelocityName());
                        if (needToQueue) {
                            handleQueueEntry(player, queuePlayer, queueOfflineText, messages.getQueuedCommandConfirmation(destinationServer.getDisplayName()), sendToQueue);
                        } else if (isPunished) {
                            handlePendingEntry(player, queuePlayer, destinationServer, queueOfflineText, messages.getQueuedCommandConfirmation(destinationServer.getDisplayName()), sendToQueue);
                        } else {
                            player.sendMessage(MatsuQueuePlugin.msg(messages.getConnecting(destinationServer.getDisplayName())));
                            handleDirectEntry(player, queuePlayer, destinationServer, destinationOfflineText);
                        }
                        return;
                    }
                    player.sendMessage(MatsuQueuePlugin.msg("\2476Server " + destinationServerKey + " does not exist."));
                    String[] keys = plugin.getDestinationServerKeys();
                    if (keys.length > 0) {
                        StringBuilder sb = new StringBuilder("\2476Valid servers: " + keys[0]);
                        for (int i = 1; i < keys.length; i++) {
                            sb.append(", ").append(keys[i]);
                        }
                        player.sendMessage(MatsuQueuePlugin.msg(sb.toString()));
                    }
                }
            }
        }
    }

    private void handleQueueEntry(Player player, QueuePlayer qp, String offlineMsg, String queuedMessage, boolean sendToQueueServer) {
        if (!plugin.getQueueMatsuServer().isOnline()) {
            player.sendMessage(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        plugin.popQueuePlayer(player.getUniqueId());
        // MUST DISCONNECT ON ERROR PAST HERE
        if (!plugin.joinQueue(player, qp, false)) {
            player.disconnect(MatsuQueuePlugin.msg(offlineMsg));
        }
        qp.setQueueState(State.QUEUED);
        qp.startNotificationTask(player, plugin, false);
        if (!sendToQueueServer) {
            player.sendMessage(MatsuQueuePlugin.msg(queuedMessage));
            plugin.getNotificationManager().sendQueueUI(player, qp, null, true);
            return;
        }
        player.createConnectionRequest(plugin.getQueueMatsuServer().getServer(plugin)).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(MatsuQueuePlugin.msg(queuedMessage));
            } else {
                player.disconnect(MatsuQueuePlugin.msg(offlineMsg));
            }
        });
    }

    private void handlePendingEntry(Player player, QueuePlayer qp, MatsuDestinationServer dest, String offlineMsg, String queuedMessage, boolean sendToQueueServer) {
        if (!dest.isOnline()) {
            player.sendMessage(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        if (!plugin.getQueueMatsuServer().isOnline()) {
            player.sendMessage(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        plugin.popQueuePlayer(player.getUniqueId());
        qp.setQueueState(State.PENDING);
        // MUST DISCONNECT ON ERROR PAST HERE
        if (!plugin.joinSlotPool(qp, false)) {
            player.disconnect(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        qp.startNotificationTask(player, plugin, false);
        if (!sendToQueueServer) {
            player.sendMessage(MatsuQueuePlugin.msg(queuedMessage));
            plugin.getNotificationManager().sendQueueUI(player, qp, null, true);
            return;
        }
        player.createConnectionRequest(plugin.getQueueMatsuServer().getServer(plugin)).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(MatsuQueuePlugin.msg(queuedMessage));
            } else {
                player.disconnect(MatsuQueuePlugin.msg(offlineMsg));
            }
        });
    }

    private void handleDirectEntry(Player player, QueuePlayer qp, MatsuDestinationServer dest, String offlineMsg) {
        if (!dest.isOnline()) {
            player.sendMessage(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        plugin.popQueuePlayer(player.getUniqueId());
        // MUST DISCONNECT ON ERROR PAST HERE
        if (!plugin.joinSlotPool(qp, false)) {
            player.disconnect(MatsuQueuePlugin.msg(offlineMsg));
            return;
        }
        qp.setQueueState(State.IDLE);
        player.createConnectionRequest(dest.getServer(plugin)).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                qp.setQueueState(State.PLAYING);
            } else {
                player.disconnect(MatsuQueuePlugin.msg(offlineMsg));
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return SimpleCommand.super.suggestAsync(invocation);
    }
}
