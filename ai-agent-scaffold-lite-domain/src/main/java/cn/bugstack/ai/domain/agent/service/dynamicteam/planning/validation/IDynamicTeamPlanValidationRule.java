package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation;

/**
 * 动态团队计划的一组粗粒度校验规则。
 */
public interface IDynamicTeamPlanValidationRule {

    void validate(DynamicTeamPlanValidationContext context);

}
