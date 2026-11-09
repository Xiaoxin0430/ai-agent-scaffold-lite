package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
/** 不暴露 WorkerPool、Future 和 Agent 实例的运行只读快照。 */
@Getter
@Builder
public class DynamicTeamRunSnapshotVO {
    private final String runId;
    private final String userGoal;
    private final List<String> acceptanceRequirements;
    private final DynamicTeamRunStatusEnum status;
    private final DynamicTeamRunTerminationReasonEnum terminationReason;
    private final List<DynamicTeamPlanVO> planHistory;
    private final DynamicTeamPlanVO currentPlan;
    private final List<DynamicTeamTaskExecutionResultVO> currentTaskResults;
    private final int usedModelCalls;
    private final int remainingModelCalls;
    private final Instant startedAt;
    private final boolean resourcesReleased;
    private final List<DynamicTeamRunEventVO> events;
    /** 仅在整次运行进入 COMPLETED 后非 null；其余终态保持 null，不返回虚假汇总。 */
    private final String finalAnswer;
}
