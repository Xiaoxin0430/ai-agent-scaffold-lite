package cn.bugstack.ai.domain.agent.model.dynamicteam.execution;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 一轮任务执行的结果集合及停止原因。 */
@Getter
@Builder
public class DynamicTeamTaskExecutionReportVO {
    private final List<DynamicTeamTaskExecutionResultVO> taskResults;
    private final DynamicTeamExecutionStopReasonEnum stopReason;
}
