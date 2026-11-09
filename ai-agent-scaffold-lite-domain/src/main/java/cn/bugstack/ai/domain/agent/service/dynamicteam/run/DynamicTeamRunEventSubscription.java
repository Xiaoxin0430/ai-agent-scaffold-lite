package cn.bugstack.ai.domain.agent.service.dynamicteam.run;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunEventVO;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 先回放历史、再消费实时队列；sequence 去重保证重连不会重复渲染。 */
public class DynamicTeamRunEventSubscription implements AutoCloseable {

    private final Queue<DynamicTeamRunEventVO> replayEvents = new ArrayDeque<>();
    private final BlockingQueue<DynamicTeamRunEventVO> liveEvents = new LinkedBlockingQueue<>(256);
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean();
    private long lastDeliveredSequence;

    DynamicTeamRunEventSubscription(long afterSequence, Runnable closeAction) {
        this.lastDeliveredSequence = Math.max(0L, afterSequence);
        this.closeAction = closeAction;
    }

    synchronized void initialize(List<DynamicTeamRunEventVO> history) {
        for (DynamicTeamRunEventVO event : history) {
            if (event.getSequence() > lastDeliveredSequence) {
                replayEvents.offer(event);
            }
        }
    }

    void offer(DynamicTeamRunEventVO event) {
        if (!closed.get()) {
            liveEvents.offer(event);
        }
    }

    public DynamicTeamRunEventVO poll(long timeout, TimeUnit unit) throws InterruptedException {
        while (!closed.get()) {
            DynamicTeamRunEventVO event;
            synchronized (this) {
                event = replayEvents.poll();
            }
            if (event == null) {
                event = liveEvents.poll(timeout, unit);
            }
            if (event == null) {
                return null;
            }
            synchronized (this) {
                if (event.getSequence() <= lastDeliveredSequence) {
                    continue;
                }
                lastDeliveredSequence = event.getSequence();
            }
            return event;
        }
        return null;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
            liveEvents.clear();
        }
    }
}
