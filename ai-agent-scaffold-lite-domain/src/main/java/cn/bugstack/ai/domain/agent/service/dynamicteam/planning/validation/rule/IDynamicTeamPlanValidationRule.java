package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule;

import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidationContext;

/**
 * 动态团队计划的一组粗粒度校验规则接口
 */
public interface IDynamicTeamPlanValidationRule {

    void validate(DynamicTeamPlanValidationContext context);

}
