package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationResultVO;

/**
 * 动态团队计划校验与正式计划创建入口。
 */
public interface IDynamicTeamPlanValidationService {

    DynamicTeamPlanValidationResultVO validateAndBuild(
            DynamicTeamPlanValidationCommandEntity command
    );

}
