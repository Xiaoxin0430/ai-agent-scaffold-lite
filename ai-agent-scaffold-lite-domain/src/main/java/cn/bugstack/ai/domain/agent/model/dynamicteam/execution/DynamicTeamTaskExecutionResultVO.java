package cn.bugstack.ai.domain.agent.model.dynamicteam.execution;

import lombok.Builder;
import lombok.Getter;

/** 本轮任务执行事实；SUCCEEDED 仅表示有有效输出，不表示评审合格。 */
@Getter
@Builder
public class DynamicTeamTaskExecutionResultVO {
    private final String taskId;
    private final String workerId;
    private final DynamicTeamTaskStatusEnum status;
    private final String output;
    private final String failureMessage;
}
