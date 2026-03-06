package network.twink.matsuqueuevelocity.util;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.ServerQueue;
import network.twink.matsuqueuevelocity.queue.State;
import network.twink.matsuqueuevelocity.server.MatsuDestinationServer;
import network.twink.matsuqueuevelocity.task.ActionBarTask;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public class MatsuQueueNotificationManager {

    private final MatsuQueuePlugin plugin;

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
            actionMsg = qp.isActionBarMessageToggled() ? status : msgs.getEstimatedTime(dest.getDisplayName(), qp.getCachedPositionInQueue(), queue.getAverageTimeBetweenJoins());
            chat = initial ? status : msgs.getPositionInQueue(dest.getDisplayName(), qp.getCachedPositionInQueue());
        } else { // PENDING
            status = isOnline ? msgs.getPendingConnection(dest.getDisplayName()) : msgs.getNowOffline(dest.getDisplayName());
            actionMsg = qp.isActionBarMessageToggled() ? status : msgs.getEstimatedTime(dest.getDisplayName(), qp.getCachedPositionInQueue(), qp.getCachedPunishmentSeconds(), false);
            chat = initial ? msgs.getWaitingConnection(dest.getDisplayName()) : msgs.getPendingConnection(dest.getDisplayName());
        }
        player.sendMessage(MatsuQueuePlugin.msg(chat));
        long eta = (qp.getQueueState() == State.QUEUED) ? queue.getAverageTimeBetweenJoins() : qp.getCachedPunishmentSeconds();
        String header = msgs.formatTabListMessage(plugin.getTabHeaderTemplateForPlayer(qp), status, qp.getCachedPositionInQueue(), eta, qp.getQueueState() == State.QUEUED);
        String footer = msgs.formatTabListMessage(plugin.getTabFooterTemplateForPlayer(qp), status, qp.getCachedPositionInQueue(), eta, qp.getQueueState() == State.QUEUED);

        player.sendPlayerListHeaderAndFooter(MatsuQueuePlugin.msg(header), MatsuQueuePlugin.msg(footer));

        // update action bar eta
        qp.cancelActionBarTask();
        ActionBarTask newAbTask = new ActionBarTask(plugin, player, qp, queue) {
            @Override
            public void accept(ScheduledTask task) {
                super.accept(task);
                if (!this.cancelled()) {
                    qp.setActionBarTask(task);
                    player.sendActionBar(MatsuQueuePlugin.msg(actionMsg));
                }
            }
        };
        qp.setActionBarTaskId(newAbTask.getId());
        plugin.getProxyServer().getScheduler().buildTask(plugin, newAbTask).repeat(1L, TimeUnit.SECONDS).schedule();
    }

}
