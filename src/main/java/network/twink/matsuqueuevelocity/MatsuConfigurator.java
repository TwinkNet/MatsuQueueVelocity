package network.twink.matsuqueuevelocity;

import network.twink.matsuqueuevelocity.queue.ServerQueue;
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

        matsuMessages.setConnecting(parser.getString("status.connecting"));
        matsuMessages.setPendingConnection(parser.getString("status.pending-connection"));
        matsuMessages.setPositionInQueue(parser.getString("status.pos-in-queue"));
        matsuMessages.setNowOffline(parser.getString("status.now-offline"));
        matsuMessages.setNowQueued(parser.getString("status.now-queued"));

        plugin.setGlobalPunishmentSeconds(parser.getInt("reconnect-cooldown"));
        plugin.setRootPermission(parser.getString("root-permission"));
        plugin.setQueueServer(new MatsuServer(parser.getString("queue-server.velocity-name"), parser.getString("queue-server.display-name")));
        plugin.setDestinationServer(new MatsuServer(parser.getString("destination-server.velocity-name"), parser.getString("destination-server.display-name")));
        ConfigSection queueSection = parser.getSection("queues");
        ConfigSection slotSection = parser.getSection("slots");
        for (String key : slotSection.getKeys(false)) {
            SlotPool pool = new SlotPool(key, slotSection.getInt(key + ".capacity", -1));
            plugin.registerSlotPool(pool);
        }
        for (String queueName : queueSection.getKeys(false)) {
            List<String> prioSlots = queueSection.getStringList(queueName + ".slots");
            ServerQueue queue = new ServerQueue(queueName, queueSection.getInt(queueName + ".priority"), prioSlots.toArray(new String[0]));
            plugin.registerQueue(queue);
        }
    }

    public MatsuMessages getMatsuMessages() {
        return matsuMessages;
    }
}
