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
    private MatsuServer destinationServer;

    private final ConcurrentHashMap<String, ServerQueue> queueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlotPool> slotMap = new ConcurrentHashMap<>();
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
            this.destinationServer.updateOnline(this);
            this.queueServer.updateOnline(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.getProxyServer().getEventManager().register(this, new MatsuEventHandler(this));
        getProxyServer().getScheduler().buildTask(this, () -> {
            purgatory.removeIf(queuePlayer -> queuePlayer.getQueueState() == State.LEFT && System.currentTimeMillis() - queuePlayer.getStateLastUpdated() > MatsuQueuePlugin.this.getMaxPunishmentSeconds() * 1000L);
            slotMap.forEach((name, slotPool) -> {
                slotPool.connectAnyPending(MatsuQueuePlugin.this);
            });
            queueMap.values().stream().sorted(Comparator.comparingInt(ServerQueue::getPriority).reversed()).forEach((serverQueue) -> {
                serverQueue.connectFirstIfPossible(MatsuQueuePlugin.this);
            });
            destinationServer.updateOnline(this);
            queueServer.updateOnline(this);
        }).repeat(500L, TimeUnit.MILLISECONDS).schedule();
        getProxyServer().getScheduler().buildTask(this, () -> {
            slotMap.forEach((name, slotPool) -> {
                slotPool.notifyAnyPending(this);
            });
            queueMap.forEach((name, serverQueue) -> {
                serverQueue.notifyAllQueueMembers(this);
            });
        }).repeat(10L, TimeUnit.SECONDS).schedule();
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

    public void registerSlotPool(SlotPool slotPool) {
        this.slotMap.put(slotPool.getName(), slotPool);
        this.getLogger().warn("Registering slot pool {} with {} player slots", slotPool.getName(), slotPool.getCapacity());
    }

    public void registerQueue(ServerQueue queue) {
        this.queueMap.put(queue.getName(), queue);
        this.getLogger().warn("Registering queue {} with a {} priority level", queue.getName(), queue.getPriority());
    }

    public int getMaxPunishmentSeconds() {
        int max = 0;
        for (ServerQueue value : queueMap.values()) {
            if (value.getPunishmentSeconds() > max) max = value.getPunishmentSeconds();
        }
        return max;
    }

    public QueuePlayer findQueuePlayer(Player player) {
        return findQueuePlayer(player.getUniqueId());
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
        for (SlotPool value : slotMap.values()) {
            QueuePlayer player = value.getQueuePlayer(uuid);
            if (player != null) {
                return player;
            }
        }
        for (ServerQueue value : queueMap.values()) {
            QueuePlayer player = value.getQueuePlayer(uuid);
            if (player != null) {
                return player;
            }
        }
        return null;
    }

    /**
     * Find a QueuePlayer in purgatory, all player slots, and all queues and remove them from whatever they were found in.
     * Useful for moving a player from one status to another when we don't know where they are.
     *
     * @param id The UUID of the QueuePlayer to be found
     * @return A QueuePlayer, or null specified QueuePlayer wasn't found.
     */
    public QueuePlayer popQueuePlayer(UUID id) {
        for (QueuePlayer queuePlayer : purgatory) {
            if (queuePlayer.getUuid().equals(id)) {
                purgatory.remove(queuePlayer);
                return queuePlayer;
            }
        }
        for (SlotPool value : slotMap.values()) {
            QueuePlayer qp = value.getAndRemovePlayer(id);
            if (qp != null) {
                return qp;
            }
        }
        for (ServerQueue value : queueMap.values()) {
            QueuePlayer qp = value.dequeue(id);
            if (qp != null) {
                return qp;
            }
        }
        return null;
    }

    public void setRootPermission(String rootPermission) {
        this.rootPermission = rootPermission;
    }

    public RegisteredServer getDestinationServer() {
        return getDestinationMatsuServer().getServer(this);
    }

    public MatsuServer getDestinationMatsuServer() {
        return destinationServer;
    }

    /**
     * @return the last known status of the Destination Server
     */
    public boolean isDestinationServerOnline() {
        return destinationServer.isOnline();
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

    public void setDestinationServer(MatsuServer destinationServer) {
        this.destinationServer = destinationServer;
    }

    public RegisteredServer getQueueServer() {
        return getQueueMatsuServer().getServer(this);
    }

    public MatsuServer getQueueMatsuServer() {
        return queueServer;
    }

    public SlotPool getSlotPool(String name) {
        return slotMap.get(name);
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
     * @param player The Player to check.
     * @return Whether the destination server should be considered "full"
     */
    public boolean isDestinationServerFull(Player player) {
        for (String s : queueMap.keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag) {
                return queueMap.get(s).isServerFull(this) || queueMap.get(s).arePlayersQueued();
            }
        }
        return queueMap.get("default").isServerFull(this) || queueMap.get("default").arePlayersQueued();
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
        for (String s : queueMap.keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                ServerQueue serverQueue = queueMap.get(s);
                return serverQueue.enqueue(queuePlayer);
            }
        }
        if (!forceDefault) {
            return joinQueue(player, queuePlayer, true);
        }
        getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        return false; // Someone forgot to configure a default server.
    }

    public ServerQueue getQueue(Player player, boolean forceDefault) {
        for (String s : queueMap.keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                return queueMap.get(s);
            }
        }
        if (!forceDefault) {
            return getQueue(player, true);
        }
        getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        return null; // Someone forgot to configure a default server.
    }

    private String getTabHeaderTemplateForPlayer(Player player, boolean forceDefault) {
        for (String s : queueMap.keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                ServerQueue serverQueue = queueMap.get(s);
                return serverQueue.getTabHeaderTemplate();
            }
        }
        if (!forceDefault) {
            return getTabHeaderTemplateForPlayer(player, true);
        }
        return "\nerror\n";
    }

    public String getTabHeaderTemplateForPlayer(Player player) {
        return getTabHeaderTemplateForPlayer(player, false);
    }


    public String getTabFooterTemplateForPlayer(Player player) {
        return getTabFooterTemplateForPlayer(player, false);
    }

    private String getTabFooterTemplateForPlayer(Player player, boolean forceDefault) {
        for (String s : queueMap.keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                ServerQueue serverQueue = queueMap.get(s);
                return serverQueue.getTabFooterTemplate();
            }
        }
        if (!forceDefault) {
            return getTabFooterTemplateForPlayer(player, true);
        }
        return "\nerror\n";
    }

    public boolean joinQueue(Player player, QueuePlayer queuePlayer) {
        return joinQueue(player, queuePlayer, false);
    }

    public boolean joinSlotPool(Player player, QueuePlayer queuePlayer, boolean forceDefault) {
        for (String s : queueMap.keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag || (forceDefault && s.equals("default"))) {
                ServerQueue serverQueue = queueMap.get(s);
                if (!serverQueue.isServerFull(this)) {
                    return serverQueue.joinSlotPool(this, queuePlayer);
                }
            }
        }
        if (!forceDefault) {
            return joinSlotPool(player, queuePlayer, true);
        }
        getLogger().error("There isn't a queue named \"default\", and this is causing problems. Add a default queue to your config, or light the server on fire.");
        return false;
    }


    public boolean joinSlotPool(Player player, QueuePlayer queuePlayer) {
        return joinSlotPool(player, queuePlayer, false);
    }
}
