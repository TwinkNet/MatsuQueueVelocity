package network.twink.matsuqueuevelocity.server;

import network.twink.matsuqueuevelocity.queue.ServerQueue;
import network.twink.matsuqueuevelocity.slot.SlotPool;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class MatsuDestinationServer extends MatsuServer {

    private final ConcurrentHashMap<String, ServerQueue> queueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlotPool> slotMap = new ConcurrentHashMap<>();

    public MatsuDestinationServer(String velocityName, String displayName) {
        super(velocityName, displayName);
    }

    public ConcurrentHashMap<String, ServerQueue> getQueueMap() {
        return queueMap;
    }

    public ConcurrentHashMap<String, SlotPool> getSlotMap() {
        return slotMap;
    }

    public void registerSlotPool(String destKey, SlotPool slotPool) {
        this.getSlotMap().put(slotPool.getName(), slotPool);
        return;
    }

    public void registerQueue(String destKey, ServerQueue queue) {
        this.getQueueMap().put(queue.getName(), queue);
        return;
    }
}
