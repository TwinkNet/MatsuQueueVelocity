package network.twink.matsuqueuevelocity;

import network.twink.matsuqueuevelocity.queue.ServerQueue;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.server.MatsuServer;
import network.twink.matsuqueuevelocity.slot.SlotPool;
import network.twink.matsuqueuevelocity.util.MatsuMessages;
import network.twink.matsuqueuevelocity.util.yml.ConfigSection;
import network.twink.matsuqueuevelocity.util.yml.YMLParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

public class MatsuConfigurator {

    private MatsuMessages matsuMessages;

    public MatsuConfigurator(MatsuQueuePlugin plugin) throws IOException {
        File dir = plugin.getDataDirectoryFile();
        if (!dir.exists()) {
            dir.mkdir();
        }
        File file = new File(dir, "config.yml");
        if (!file.exists()) {
            if (file.createNewFile()) {
                Path p = file.toPath();
                Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), p, StandardCopyOption.REPLACE_EXISTING);
            }  else {
                throw new RuntimeException("Could not create config.yml");
            }
        }
        this.matsuMessages = new MatsuMessages();
        YMLParser parser = new YMLParser(file);

        matsuMessages.setConnecting(parser.getString("status.connecting").replace("\\n", "\n"));
        matsuMessages.setPendingConnection(parser.getString("status.pending-connection").replace("\\n", "\n"));
        matsuMessages.setWaitingConnection(parser.getString("status.waiting-connection").replace("\\n", "\n"));
        matsuMessages.setPositionInQueue(parser.getString("status.pos-in-queue").replace("\\n", "\n"));
        matsuMessages.setNowOffline(parser.getString("status.now-offline").replace("\\n", "\n"));
        matsuMessages.setNowQueued(parser.getString("status.now-queued").replace("\\n", "\n"));
        matsuMessages.setEstimatedTime(parser.getString("status.estimated-time").replace("\\n", "\n"));

        plugin.setRootPermission(parser.getString("root-permission"));
        plugin.setQueueServer(new MatsuServer(parser.getString("queue-server.velocity-name"), parser.getString("queue-server.display-name")));
        // plugin.setDestinationServer(new MatsuServer(parser.getString("destination-server.velocity-name"), parser.getString("destination-server.display-name")));
        ConfigSection destinationServers = parser.getSection("destination-servers");
        for (String destVelName : destinationServers.getKeys(false)) {
            MatsuDestinationServer destinationServer = new MatsuDestinationServer(destVelName, destinationServers.getString(destVelName + ".display-name"));
            ConfigSection queueSection = destinationServers.getSection(destVelName + ".queues");
            ConfigSection slotSection = destinationServers.getSection(destVelName + ".slots");
            for (String key : slotSection.getKeys(false)) {
                SlotPool pool = new SlotPool(key, slotSection.getInt(key + ".capacity", -1));
                destinationServer.registerSlotPool(destVelName, pool);
                plugin.getLogger().warn("Registering slot pool {} with {} player slots", pool.getName(), pool.getCapacity());
            }
            for (String queueName : queueSection.getKeys(false)) {
                List<String> prioSlots = queueSection.getStringList(queueName + ".slots");
                int punishmentSeconds = queueSection.getInt(queueName + ".reconnect-cooldown", 22);
                ServerQueue queue = new ServerQueue(queueName, destVelName, queueSection.getInt(queueName + ".priority"), prioSlots.toArray(new String[0]), punishmentSeconds);
                queue.setTabHeaderTemplate(queueSection.getString(queueName + ".tab-header", "Config Error").replace("\\n", "\n"));
                queue.setTabFooterTemplate(queueSection.getString(queueName + ".tab-footer", "Config Error").replace("\\n", "\n"));
                destinationServer.registerQueue(destVelName, queue);
                plugin.getLogger().warn("Registering queue {} with {} priority", queue.getName(), queue.getPriority());
            }
        }




    }

    public MatsuMessages getMatsuMessages() {
        return matsuMessages;
    }
}
