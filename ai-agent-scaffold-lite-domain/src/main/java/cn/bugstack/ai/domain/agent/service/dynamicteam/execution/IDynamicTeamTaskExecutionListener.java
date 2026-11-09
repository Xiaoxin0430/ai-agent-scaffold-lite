package cn.bugstack.ai.domain.agent.service.dynamicteam.execution;

import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionProgressVO;

/** 将调度过程转换为运行级事件的窄接口。 */
@FunctionalInterface
public interface IDynamicTeamTaskExecutionListener {
    void onTaskStatusChanged(DynamicTeamTaskExecutionProgressVO progress);
}
