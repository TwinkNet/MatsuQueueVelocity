package network.twink.matsuqueuevelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.ServerQueue;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.server.MatsuServer;
import network.twink.matsuqueuevelocity.slot.SlotPool;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

@Plugin(id = "matsuqueuevelocity", name = "MatsuQueueVelocity", version = BuildConstants.VERSION)
public class MatsuQueuePlugin {

    private final Logger logger;
    private final ProxyServer server;
    private final Path dataDirectory;
    private MatsuConfigurator configurator;

    private String rootPermission;

    private MatsuServer queueServer;
    private final ConcurrentHashMap<String, MatsuDestinationServer> destinationServers = new ConcurrentHashMap<>();

    public final LinkedBlockingDeque<QueuePlayer> purgatory = new LinkedBlockingDeque<>();

    @Inject
    public MatsuQueuePlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.getLogger().info("MatsuQueuePlugin has been initialized");
        try {
            configurator = new MatsuConfigurator(this);
            this.updateDestinationServersOnlineStatus();
            this.queueServer.updateOnline(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.getProxyServer().getEventManager().register(this, new MatsuEventHandler(this));
        getProxyServer().getScheduler().buildTask(this, () -> {
            purgatory.removeIf(queuePlayer -> queuePlayer.getQueueState() == State.LEFT && System.currentTimeMillis() - queuePlayer.getStateLastUpdated() > MatsuQueuePlugin.this.getMaxPunishmentSeconds() * 1000L);
            destinationServers.forEach((s, server) -> server.getSlotMap().forEach((name, slotPool) -> slotPool.connectAnyPending(server, MatsuQueuePlugin.this)));
            destinationServers.forEach((s, server) -> {
                server.getQueueMap().values().stream().sorted(Comparator.comparingInt(ServerQueue::getPriority).reversed()).forEach((serverQueue) -> {
                    serverQueue.connectFirstIfPossible(MatsuQueuePlugin.this);
                });
            });
            this.updateDestinationServersOnlineStatus();
            queueServer.updateOnline(this);
        }).repeat(500L, TimeUnit.MILLISECONDS).schedule();
        getProxyServer().getScheduler().buildTask(this, () -> {
            this.destinationServers.forEach((s, server) -> {
                server.getSlotMap().forEach((ss, slotPool) -> {
                    slotPool.notifyAnyPending(this);
                });
            });
            this.destinationServers.forEach((s, server) -> {
                server.getQueueMap().forEach((name, serverQueue) -> {
                    serverQueue.notifyAllQueueMembers(this);
                });
            });
        }).repeat(10L, TimeUnit.SECONDS).schedule();
    }


    public void registerDestinationServer(MatsuDestinationServer destinationServer) {
        this.destinationServers.put(destinationServer.getVelocityName(), destinationServer);
    }

    /**
     * Find a QueuePlayer in purgatory, all player slots, and all queues.
     * Useful for moving a player from one status to another when we don't know where they are.
     * Do not use this method to find a QueuePlayer and assign them to a queue or a slot,
     * you will create a ghost QueuePlayer and probably cause problems.
     * Use popQueuePlayer() if you need to make changes to where a QueuePlayer belongs.
     *
     * @param uuid The UUID of the QueuePlayer to be found
     * @return A QueuePlayer, or null specified QueuePlayer wasn't found.
     */
    public QueuePlayer findQueuePlayer(UUID uuid) {
        for (QueuePlayer queuePlayer : purgatory) {
            if (queuePlayer.getUuid().equals(uuid)) {
                return queuePlayer;
            }
        }
        for (MatsuDestinationServer val : destinationServers.values()) {
            for (SlotPool value : val.getSlotMap().values()) {
                QueuePlayer player = value.getQueuePlayer(uuid);
                if (player != null) {
                    return player;
                }
            }
        }
        for (MatsuDestinationServer val : destinationServers.values()) {
            for (ServerQueue value : val.getQueueMap().values()) {
                QueuePlayer player = value.getQueuePlayer(uuid);
                if (player != null) {
                    return player;
                }
            }
        }
        return null;
    }

    /**
     * Find a QueuePlayer in purgatory, all player slots, and all queues and remove them from whatever they were found in.
     * Useful for moving a player from one status to another when we don't know where they are.
     *
     * @param uuid The UUID of the QueuePlayer to be found
     * @return A QueuePlayer, or null specified QueuePlayer wasn't found.
     */
    public QueuePlayer popQueuePlayer(UUID uuid) {
        for (QueuePlayer queuePlayer : purgatory) {
            if (queuePlayer.getUuid().equals(uuid)) {
                purgatory.remove(queuePlayer);
                return queuePlayer;
            }
        }
        for (MatsuDestinationServer val : destinationServers.values()) {
            for (SlotPool value : val.getSlotMap().values()) {
                QueuePlayer player = value.getAndRemovePlayer(uuid);
                if (player != null) {
                    return player;
                }
            }
        }
        for (MatsuDestinationServer val : destinationServers.values()) {
            for (ServerQueue value : val.getQueueMap().values()) {
                QueuePlayer player = value.dequeue(uuid);
                if (player != null) {
                    return player;
                }
            }
        }
        return null;
    }

    public QueuePlayer findQueuePlayer(Player player) {
        return findQueuePlayer(player.getUniqueId());
    }

    public void setRootPermission(String rootPermission) {
        this.rootPermission = rootPermission;
    }

    public RegisteredServer getDestinationServer(String key) {
        return getDestinationMatsuServer(key).getServer(this);
    }

    public MatsuDestinationServer getDestinationMatsuServer(String key) {
        return destinationServers.get(key);
    }

    public MatsuDestinationServer getDestinationMatsuServer(QueuePlayer queuePlayer) {
        return queuePlayer.getDestinationMatsuServer(this);
    }

    public void updateDestinationServersOnlineStatus() {
        this.destinationServers.values().forEach((serverQueue) -> {
            serverQueue.updateOnline(this);
        });
    }

    /**
     * @return the last known status of the Destination Server
     */
    public boolean isDestinationServerOnline(String key) {
        return getDestinationMatsuServer(key).isOnline();
    }

    /**
     * @return the last known status of the Queue Server
     */
    public boolean isQueueServerOnline() {
        return queueServer.isOnline();
    }

    public void setQueueServer(MatsuServer queueServer) {
        this.queueServer = queueServer;
    }

    public RegisteredServer getQueueServer() {
        return getQueueMatsuServer().getServer(this);
    }

    public MatsuServer getQueueMatsuServer() {
        return queueServer;
    }

    public SlotPool getSlotPool(String destKey, String name) {
        MatsuDestinationServer server = destinationServers.get(destKey);
        if (server != null) {
            return server.getSlotMap().get(name);
        }
        return null;
    }

    /**
     * Check if the destination server should be considered full for a certain player.
     * <p>
     * This method takes into account the permissions a player has.
     * The server may not be considered full to a player with priority queue status,
     * but may be considered full for a player without priority queue status.
     * <p>
     * The server will be considered "full" to this method when either statement is true:
     * - The slots that the player's assigned queue are allowed to access are full.
     * - Anybody is queued in the player's assigned queue for the server.
     *
     * @param player The QueuePlayer to check.
     * @return Whether the destination server should be considered "full"
     */

    public boolean isDestinationServerFull(QueuePlayer player) {
        MatsuDestinationServer destinationServer = player.getDestinationMatsuServer(this);
        if (destinationServer == null) {
            throw new IllegalArgumentException(player.getDestinationServerKey() + ": is not a valid server");
        }
        ServerQueue queue = destinationServer.getQueueMap().get(player.getCachedQueueKey());
        if (queue == null) return true;
        return queue.isServerFull(this) || queue.arePlayersQueued();
    }

    /**
     * Detects the queue that the specified player belongs to and joins it.
     *
     * @param player
     * @param queuePlayer
     * @param forceDefault
     * @return Whether this operation was successful.
     */
    public boolean joinQueue(Player player, QueuePlayer queuePlayer, boolean forceDefault) {
        if (!player.getUniqueId().equals(queuePlayer.getUuid())) {
            throw new IllegalArgumentException("Mis-matching parameters: Player and QueuePlayer must represent the same person.");
        }
        MatsuDestinationServer dest = queuePlayer.getDestinationMatsuServer(this);
        if (dest == null) {
            throw new IllegalArgumentException(queuePlayer.getDestinationServerKey() + ": is not a valid server");
        }
        for (String s : dest.getQueueMap().keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                ServerQueue serverQueue = dest.getQueueMap().get(s);
                return serverQueue.enqueue(queuePlayer);
            }
        }
        if (!forceDefault) {
            return joinQueue(player, queuePlayer, true);
        }
        getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        return false; // Someone forgot to configure a default server.
    }

    public boolean joinQueue(Player player, QueuePlayer queuePlayer) {
        return joinQueue(player, queuePlayer, false);
    }

    public boolean joinQueue(QueuePlayer queuePlayer) {
        MatsuDestinationServer server = queuePlayer.getDestinationMatsuServer(this);
        if (server == null) {
            throw new IllegalArgumentException(queuePlayer.getDestinationServerKey() + ": is not a valid server");
        }
        ServerQueue serverQueue = server.getQueueMap().get(queuePlayer.getCachedQueueKey());
        boolean flag = serverQueue == null || serverQueue.enqueue(queuePlayer);
        if (!flag) {
            getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        }
        return flag; // Someone forgot to configure a default server.
    }

    public ServerQueue getQueue(QueuePlayer queuePlayer, boolean forceDefault) {
        MatsuDestinationServer server = queuePlayer.getDestinationMatsuServer(this);
        Player player = queuePlayer.getPlayer(this);
        if (server == null) {
            throw new IllegalArgumentException(queuePlayer.getDestinationServerKey() + ": is not a valid server");
        }
        if (player == null) {
            throw new IllegalArgumentException(queuePlayer.getUuid().toString() + ": is not online");
        }
        for (String s : server.getQueueMap().keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                return server.getQueueMap().get(s);
            }
        }
        if (!forceDefault) {
            return getQueue(queuePlayer, true);
        }
        getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        return null; // Someone forgot to configure a default server.
    }

    public ServerQueue getQueue(MatsuDestinationServer server, Player player, boolean forceDefault) {
        for (String s : server.getQueueMap().keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                return server.getQueueMap().get(s);
            }
        }
        if (!forceDefault) {
            return getQueue(server, player, true);
        }
        getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        return null; // Someone forgot to configure a default server.
    }

    public boolean joinSlotPool(QueuePlayer queuePlayer, boolean forceDefault) {
        Player player = queuePlayer.getPlayer(this);
        MatsuDestinationServer server = queuePlayer.getDestinationMatsuServer(this);
        if (server == null) {
            throw new IllegalArgumentException(queuePlayer.getDestinationServerKey() + ": is not a valid server");
        }
        if (player == null) {
            throw new IllegalArgumentException(queuePlayer.getUuid().toString() + ": is not online");
        }
        for (String s : server.getQueueMap().keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                ServerQueue serverQueue = server.getQueueMap().get(s);
                if (!serverQueue.isServerFull(this)) {
                    return serverQueue.joinSlotPool(this, queuePlayer);
                }
            }
        }
        if (!forceDefault) {
            return joinSlotPool(queuePlayer, true);
        }
        getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        return false;
    }

    public boolean joinSlotPool(QueuePlayer queuePlayer) {
        MatsuDestinationServer server = queuePlayer.getDestinationMatsuServer(this);
        if (server == null) {
            throw new IllegalArgumentException(queuePlayer.getDestinationServerKey() + ": is not a valid server");
        }
        ServerQueue serverQueue = server.getQueueMap().get(queuePlayer.getCachedQueueKey());
        boolean flag = serverQueue == null || serverQueue.joinSlotPool(this, queuePlayer);
        if (!flag) {
            getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        }
        return flag;
    }

    private String getTabHeaderTemplateForPlayer(QueuePlayer player, boolean forceDefault) {
        MatsuDestinationServer serv = player.getDestinationMatsuServer(this);
        Player proxPlayer = player.getPlayer(this);
        for (String s : serv.getQueueMap().keySet()) {
            boolean flag = proxPlayer.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                ServerQueue serverQueue = serv.getQueueMap().get(s);
                return serverQueue.getTabHeaderTemplate();
            }
        }
        if (!forceDefault) {
            return getTabHeaderTemplateForPlayer(player, true);
        }
        return "\nerror\n";
    }

    public String getTabHeaderTemplateForPlayer(QueuePlayer player) {
        return getTabHeaderTemplateForPlayer(player, false);
    }


    public String getTabFooterTemplateForPlayer(QueuePlayer player) {
        return getTabFooterTemplateForPlayer(player, false);
    }

    private String getTabFooterTemplateForPlayer(QueuePlayer player, boolean forceDefault) {
        MatsuDestinationServer serv = player.getDestinationMatsuServer(this);
        Player proxPlayer = player.getPlayer(this);
        for (String s : serv.getQueueMap().keySet()) {
            boolean flag = proxPlayer.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                ServerQueue serverQueue = serv.getQueueMap().get(s);
                return serverQueue.getTabFooterTemplate();
            }
        }
        if (!forceDefault) {
            return getTabFooterTemplateForPlayer(player, true);
        }
        return "\nerror\n";
    }

    public int getMaxPunishmentSeconds() {
        int max = 0;
        for (MatsuDestinationServer val : destinationServers.values()) {
            for (ServerQueue value : val.getQueueMap().values()) {
                if (value.getPunishmentSeconds() > max) max = value.getPunishmentSeconds();
            }
        }
        return max;
    }


    public ProxyServer getProxyServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public File getDataDirectoryFile() {
        return dataDirectory.toFile();
    }

    public MatsuConfigurator getConfigurator() {
        return configurator;
    }
}
