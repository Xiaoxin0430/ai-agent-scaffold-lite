package cn.bugstack.ai.domain.agent.service.dynamicteam.planning;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;

/**
 * 动态团队规划服务。
 */
public interface IDynamicTeamPlannerService {

    DynamicTeamPlanDraftVO plan(DynamicTeamPlanningCommandEntity command);

}
