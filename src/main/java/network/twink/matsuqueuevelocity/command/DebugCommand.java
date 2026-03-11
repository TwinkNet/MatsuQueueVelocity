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

public class DebugCommand implements SimpleCommand {

    private final MatsuQueuePlugin plugin;

    public DebugCommand(MatsuQueuePlugin matsuQueuePlugin) {
        this.plugin = matsuQueuePlugin;
    }

    @Override
    public void execute(Invocation invocation) {
        int scheduledTaskSize = plugin.getProxyServer().getScheduler().tasksByPlugin(plugin).size();
        int purgatorySize = plugin.purgatory.size();
        invocation.source().sendMessage(Component.text("MatsuQueueVelocity has " + scheduledTaskSize + " scheduled task(s)."));
        invocation.source().sendMessage(Component.text("MatsuQueueVelocity's Purgatory has " + purgatorySize + " members."));
        long usedMegabytes = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1000000;
        long totalMegabytes = (Runtime.getRuntime().totalMemory()) / 1000000;
        invocation.source().sendMessage(Component.text("Velocity memory usage: " + usedMegabytes + "MB used of " + totalMegabytes + " MB"));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return SimpleCommand.super.suggestAsync(invocation);
    }
}
