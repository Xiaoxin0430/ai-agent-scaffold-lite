package cn.bugstack.ai.domain.agent.service.dynamicteam.execution;

import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamExecutionControlEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerPoolEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionReportVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;

public interface IDynamicTeamTaskSchedulerService {
    /** 同一 WorkerPool 的调用与成员变更必须由上层串行编排。 */
    DynamicTeamTaskExecutionReportVO execute(String userGoal, DynamicTeamPlanVO plan,
            DynamicTeamWorkerPoolEntity pool, DynamicTeamRuntimePolicyVO policy,
            DynamicTeamExecutionControlEntity control);
}
