package cn.bugstack.ai.domain.agent.adapter.port.dynamicteam;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunEventVO;

/** 将运行事实事件推送给进程内订阅者；实现必须快速、非阻塞。 */
@FunctionalInterface
public interface IDynamicTeamRunEventPublisher {

    IDynamicTeamRunEventPublisher NO_OP = event -> { };

    void publish(DynamicTeamRunEventVO event);
}
