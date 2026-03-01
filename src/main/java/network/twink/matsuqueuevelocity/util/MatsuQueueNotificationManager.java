package network.twink.matsuqueuevelocity.util;

import com.velocitypowered.api.proxy.Player;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.ServerQueue;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public class MatsuQueueNotificationManager {

    private final MatsuQueuePlugin plugin;
    private boolean toggle;

    public MatsuQueueNotificationManager(MatsuQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("DataFlowIssue")
    public void sendQueueUI(Player player, QueuePlayer qp, @Nullable ServerQueue queue, boolean initial) {
        MatsuMessages msgs = plugin.getConfigurator().getMatsuMessages();
        MatsuDestinationServer dest = qp.getDestinationMatsuServer(plugin);
        boolean isOnline = dest.isOnline();
        if (queue == null && qp.getQueueState() == State.QUEUED) {
            queue = plugin.getQueue(qp, false);
        }
        // set messages depending on State
        String status;
        String actionMsg;
        String chat;
        if (qp.getQueueState() == State.QUEUED) {
            status = isOnline ? msgs.getNowQueued(dest.getDisplayName()) : msgs.getNowOffline(dest.getDisplayName());
            actionMsg = toggle ? status : msgs.getEstimatedTime(dest.getDisplayName(), qp.getCachedPositionInQueue(), queue.getAverageTimeBetweenJoins());
            chat = initial ? status : msgs.getPositionInQueue(dest.getDisplayName(), qp.getCachedPositionInQueue());
        } else { // PENDING
            status = isOnline ? msgs.getPendingConnection(dest.getDisplayName()) : msgs.getNowOffline(dest.getDisplayName());
            actionMsg = toggle ? status : msgs.getEstimatedTime(dest.getDisplayName(), qp.getCachedPositionInQueue(), qp.getCachedPunishmentSeconds(), false);
            chat = initial ? msgs.getWaitingConnection(dest.getDisplayName()) : msgs.getPendingConnection(dest.getDisplayName());
        }
        player.sendMessage(MatsuQueuePlugin.msg(chat));
        long eta = (qp.getQueueState() == State.QUEUED) ? queue.getAverageTimeBetweenJoins() : qp.getCachedPunishmentSeconds();
        String header = msgs.formatTabListMessage(plugin.getTabHeaderTemplateForPlayer(qp), status, qp.getCachedPositionInQueue(), eta, qp.getQueueState() == State.QUEUED);
        String footer = msgs.formatTabListMessage(plugin.getTabFooterTemplateForPlayer(qp), status, qp.getCachedPositionInQueue(), eta, qp.getQueueState() == State.QUEUED);

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

    public void toggle() {
        toggle = !toggle;
    }

}
