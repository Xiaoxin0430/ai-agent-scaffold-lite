package cn.bugstack.ai.domain.agent.service.dynamicteam.run;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamRunEventPublisher;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunContextAggregate;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunEventVO;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内运行事件中心；只入队，不在聚合锁内执行网络发送。 */
@Service
public class DynamicTeamRunEventHub implements IDynamicTeamRunEventPublisher {

    private static final int MAX_SUBSCRIBERS_PER_RUN = 3;
    private final ConcurrentHashMap<String, Set<DynamicTeamRunEventSubscription>> subscriptions =
            new ConcurrentHashMap<>();

    @Override
    public void publish(DynamicTeamRunEventVO event) {
        Set<DynamicTeamRunEventSubscription> runSubscriptions = subscriptions.get(event.getRunId());
        if (runSubscriptions == null) {
            return;
        }
        for (DynamicTeamRunEventSubscription subscription : runSubscriptions) {
            subscription.offer(event);
        }
    }

    public DynamicTeamRunEventSubscription subscribe(
            DynamicTeamRunContextAggregate context, long afterSequence) {
        String runId = context.getRunId();
        Set<DynamicTeamRunEventSubscription> runSubscriptions = subscriptions.computeIfAbsent(
                runId, key -> ConcurrentHashMap.newKeySet());
        DynamicTeamRunEventSubscription[] holder = new DynamicTeamRunEventSubscription[1];
        holder[0] = new DynamicTeamRunEventSubscription(afterSequence, () -> {
            runSubscriptions.remove(holder[0]);
            if (runSubscriptions.isEmpty()) {
                subscriptions.remove(runId, runSubscriptions);
            }
        });
        synchronized (runSubscriptions) {
            if (runSubscriptions.size() >= MAX_SUBSCRIBERS_PER_RUN) {
                throw new IllegalStateException("该运行的 SSE 连接数已达上限。");
            }
            runSubscriptions.add(holder[0]);
        }
        // 先注册再读取快照；并发到达的事件会进入 live 队列，sequence 在消费时去重。
        holder[0].initialize(context.snapshot().getEvents());
        return holder[0];
    }
}
