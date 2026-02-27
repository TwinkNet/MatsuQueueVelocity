package network.twink.matsuqueuevelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import command.QueueCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.ServerQueue;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.server.MatsuServer;
import network.twink.matsuqueuevelocity.slot.SlotPool;
import org.slf4j.Logger;

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
        try {
            configurator = new MatsuConfigurator(this);
            this.updateDestinationServersOnlineStatus();
            this.queueServer.updateOnline(this);
        } catch (IOException e) {
            logger.error("Failed to load MatsuConfigurator!", e);
        }

        server.getEventManager().register(this, new MatsuEventHandler(this));

        // Main Queue Logic Task (500ms)
        server.getScheduler().buildTask(this, this::runQueueTick)
                .repeat(500L, TimeUnit.MILLISECONDS)
                .schedule();

        // Notification Task (10s)
        server.getScheduler().buildTask(this, this::runNotificationTick)
                .repeat(10L, TimeUnit.SECONDS)
                .schedule();
        CommandManager commandManager = getProxyServer().getCommandManager();
        CommandMeta meta = commandManager.metaBuilder("queue")
                .aliases("q")
                .plugin(this)
                .build();

        SimpleCommand command = new QueueCommand(this);
        commandManager.register(meta, command);
        logger.info("MatsuQueuePlugin has been initialized");
    }

    private void runQueueTick() {
        // purge offline players who aren't coming back
        long maxPunishmentMs = getMaxPunishmentSeconds() * 1000L;
        purgatory.removeIf(qp -> qp.getQueueState() == State.LEFT &&
                (System.currentTimeMillis() - qp.getStateLastUpdated() > maxPunishmentMs));

        // connect pending players who have satisfied their queue's timeout.
        destinationServers.values().forEach(dest -> {
            dest.getSlotMap().values().forEach(pool -> pool.connectAnyPending(dest, this));

            dest.getQueueMap().values().stream()
                    .sorted(Comparator.comparingInt(ServerQueue::getPriority).reversed())
                    .forEach(queue -> queue.connectFirstIfPossible(this));
        });
        // check if the destination server is still online
        updateDestinationServersOnlineStatus();
        queueServer.updateOnline(this);
    }

    private void runNotificationTick() {
        destinationServers.values().forEach(dest -> {
            dest.getSlotMap().values().forEach(pool -> pool.notifyAnyPending(this));
            dest.getQueueMap().values().forEach(queue -> queue.notifyAllQueueMembers(this));
        });
    }

    // --- Player Lookup Logic ---

    public QueuePlayer findQueuePlayer(UUID uuid) {
        return searchPlayers(uuid, false);
    }

    public QueuePlayer popQueuePlayer(UUID uuid) {
        return searchPlayers(uuid, true);
    }

    private QueuePlayer searchPlayers(UUID uuid, boolean remove) {
        // Check Purgatory
        for (QueuePlayer qp : purgatory) {
            if (qp.getUuid().equals(uuid)) {
                if (remove) purgatory.remove(qp);
                return qp;
            }
        }
        // Check Slots and Queues
        for (MatsuDestinationServer dest : destinationServers.values()) {
            for (SlotPool pool : dest.getSlotMap().values()) {
                QueuePlayer qp = remove ? pool.getAndRemovePlayer(uuid) : pool.getQueuePlayer(uuid);
                if (qp != null) return qp;
            }
            for (ServerQueue queue : dest.getQueueMap().values()) {
                QueuePlayer qp = remove ? queue.dequeue(uuid) : queue.getQueuePlayer(uuid);
                if (qp != null) return qp;
            }
        }
        return null;
    }

    // --- Queue Join/Resolution Logic ---

    private ServerQueue resolveQueue(Player player, MatsuDestinationServer dest, boolean forceDefault) {
        for (String key : dest.getQueueMap().keySet()) {
            if (player.hasPermission(rootPermission + "." + key) || (forceDefault && key.equals("default"))) {
                return dest.getQueueMap().get(key);
            }
        }
        if (!forceDefault) return resolveQueue(player, dest, true);

        logger.error("No 'default' queue found for server: {}", dest.getVelocityName());
        return null;
    }

    public boolean joinQueue(Player player, QueuePlayer queuePlayer, boolean forceDefault) {
        if (!player.getUniqueId().equals(queuePlayer.getUuid())) {
            throw new IllegalArgumentException("Player and QueuePlayer UUID mismatch.");
        }
        MatsuDestinationServer dest = queuePlayer.getDestinationMatsuServer(this);
        if (dest == null) throw new IllegalArgumentException("Invalid destination server.");

        ServerQueue target = resolveQueue(player, dest, forceDefault);
        return target != null && target.enqueue(queuePlayer);
    }

    public boolean joinSlotPool(QueuePlayer queuePlayer, boolean forceDefault) {
        Player player = queuePlayer.getPlayer(this);
        MatsuDestinationServer dest = queuePlayer.getDestinationMatsuServer(this);

        if (dest == null || player == null) return false;

        ServerQueue target = resolveQueue(player, dest, forceDefault);
        if (target != null && !target.isServerFull(queuePlayer, this)) {
            return target.joinSlotPool(this, queuePlayer);
        }
        return false;
    }

    // --- "Simple" Getters / Setters ---

    public void registerDestinationServer(MatsuDestinationServer destinationServer) {
        this.destinationServers.put(destinationServer.getVelocityName(), destinationServer);
    }

    public boolean isDestinationServerFull(QueuePlayer player) {
        MatsuDestinationServer dest = player.getDestinationMatsuServer(this);
        if (dest == null) throw new IllegalArgumentException(player.getDestinationServerKey() + " is invalid");

        ServerQueue queue = dest.getQueueMap().get(player.getCachedQueueKey());
        return queue == null || queue.isServerFull(player, this) || queue.arePlayersQueued();
    }

    public String getTabHeaderTemplateForPlayer(QueuePlayer player) {
        ServerQueue q = getQueue(player, false);
        return (q != null) ? q.getTabHeaderTemplate() : "\nerror\n";
    }

    public String getTabFooterTemplateForPlayer(QueuePlayer player) {
        ServerQueue q = getQueue(player, false);
        return (q != null) ? q.getTabFooterTemplate() : "\nerror\n";
    }

    public ServerQueue getQueue(QueuePlayer qp, boolean forceDefault) {
        Player p = qp.getPlayer(this);
        MatsuDestinationServer d = qp.getDestinationMatsuServer(this);
        return (p == null || d == null) ? null : resolveQueue(p, d, forceDefault);
    }

    public void updateDestinationServersOnlineStatus() {
        destinationServers.values().forEach(s -> s.updateOnline(this));
    }

    public int getMaxPunishmentSeconds() {
        return destinationServers.values().stream()
                .flatMap(s -> s.getQueueMap().values().stream())
                .mapToInt(ServerQueue::getPunishmentSeconds)
                .max().orElse(0);
    }

    public static Component msg(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
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

    public MatsuConfigurator getConfigurator() {
        return configurator;
    }

    public void setRootPermission(String rootPermission) {
        this.rootPermission = rootPermission;
    }

    public void setQueueServer(MatsuServer queueServer) {
        this.queueServer = queueServer;
    }

    public MatsuServer getQueueMatsuServer() {
        return queueServer;
    }

    public MatsuDestinationServer getDestServer(String key) {
        return destinationServers.get(key);
    }

    public String[] getDestinationServerKeys() {
        return destinationServers.keySet().toArray(new String[0]);
    }

    public RegisteredServer getVelocityServer(String key) {
        return getDestServer(key).getServer(this);
    }
}