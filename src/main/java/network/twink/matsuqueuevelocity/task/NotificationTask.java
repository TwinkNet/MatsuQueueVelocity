package network.twink.matsuqueuevelocity.task;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;
import network.twink.matsuqueuevelocity.queue.QueuePlayer;
import network.twink.matsuqueuevelocity.queue.ServerQueue;

import java.util.function.Consumer;

public class NotificationTask implements Consumer<ScheduledTask> {

    private final long id;
    private QueuePlayer queuePlayer;
    private Player player;
    private MatsuQueuePlugin plugin;
    private ServerQueue serverQueue;
    private final boolean[] delays = new boolean[]{true}; // use to delay 10sec

    public NotificationTask(MatsuQueuePlugin plugin, Player player, QueuePlayer queuePlayer, ServerQueue queue) {
        this.plugin = plugin;
        this.player = player;
        this.queuePlayer = queuePlayer;
        this.serverQueue = queue;
        id = plugin.getRandom().nextLong();
    }

    @Override
    public void accept(ScheduledTask task) {
        if (!player.isActive() || task.status() == TaskStatus.CANCELLED || queuePlayer.getNotificationTaskId() != this.id) {
            task.cancel();
            return;
        }
        queuePlayer.setNotificationTask(task);
        if (delays[0]) {
            delays[0] = false;
            return;
        }
        queuePlayer.toggleActionBar();
        plugin.getNotificationManager().sendQueueUI(player, queuePlayer, serverQueue, false);
    }

    public long getId() {
        return id;
    }
}
