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
    private int globalPunishmentSeconds;

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
        this.getProxyServer().getEventManager().register(this, new MatsuEvents(this));
        getProxyServer().getScheduler().buildTask(this, () -> {
            purgatory.removeIf(queuePlayer -> queuePlayer.getQueueState() == State.LEFT && System.currentTimeMillis() - queuePlayer.getStateLastUpdated() > MatsuQueuePlugin.this.getGlobalPunishmentSeconds() * 1000L);
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
        this.slotMap.put(slotPool.getName(),  slotPool);
        this.getLogger().warn("Registering slot pool {} with {} player slots", slotPool.getName(), slotPool.getCapacity());
    }

    public void registerQueue(ServerQueue queue) {
        this.queueMap.put(queue.getName(), queue);
        this.getLogger().warn("Registering queue {} with a {} priority level", queue.getName(), queue.getPriority());
    }

    public void setGlobalPunishmentSeconds(int globalPunishmentSeconds) {
        this.globalPunishmentSeconds = globalPunishmentSeconds;
    }

    public int getGlobalPunishmentSeconds() {
        return globalPunishmentSeconds;
    }

    public QueuePlayer findQueuePlayer(Player player) {
        return findQueuePlayer(player.getUniqueId());
    }

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

    public boolean isDestinationServerOnline() {
        return destinationServer.isOnline();
    }
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

    public boolean isDestinationServerFull(Player player) {
        for (String s : queueMap.keySet()) {
            boolean flag = player.getPermissionChecker().test(rootPermission + "." + s);
            if (flag) {
                return queueMap.get(s).isServerFull(this) || queueMap.get(s).arePlayersQueued();
            }
        }
        return queueMap.get("default").isServerFull(this) || queueMap.get("default").arePlayersQueued();
    }

    public boolean joinQueue(Player player, QueuePlayer queuePlayer, boolean forceDefault) {
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
        return false; // Server must be full.
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
        return false; // Server must be full.
    }


    public boolean joinSlotPool(Player player, QueuePlayer queuePlayer) {
        return joinSlotPool(player, queuePlayer, false);
    }
}
