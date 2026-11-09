package cn.bugstack.ai.domain.agent.model.dynamicteam.execution;

import lombok.Builder;
import lombok.Getter;

/** 调度器向运行上下文报告的单个任务状态变化。 */
@Getter
@Builder
public class DynamicTeamTaskExecutionProgressVO {
    private final String taskId;
    private final String workerId;
    private final DynamicTeamTaskStatusEnum status;
    private final String failureMessage;
}
