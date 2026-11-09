package cn.bugstack.ai.domain.agent.adapter.port.dynamicteam;

import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamModelCallBudgetEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;

/**
 * 临时 Worker Agent 创建端口。
 */
public interface IDynamicTeamWorkerAgentFactory {

    DynamicTeamWorkerRuntimeEntity create(
            String runId,
            int createdPlanVersion,
            DynamicTeamWorkerVO worker,
            DynamicTeamModelCallBudgetEntity modelCallBudget
    );

}
