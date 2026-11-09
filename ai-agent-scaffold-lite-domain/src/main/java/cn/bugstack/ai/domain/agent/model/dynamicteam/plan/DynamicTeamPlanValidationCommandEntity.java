package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次计划校验需要的后端上下文。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamPlanValidationCommandEntity {

    private String runId;
    private DynamicTeamPlanningModeEnum planningMode;
    private DynamicTeamPlanDraftVO planDraft;
    private DynamicTeamRuntimePolicyVO runtimePolicy;
    private DynamicTeamPlanVO previousPlan;

}
