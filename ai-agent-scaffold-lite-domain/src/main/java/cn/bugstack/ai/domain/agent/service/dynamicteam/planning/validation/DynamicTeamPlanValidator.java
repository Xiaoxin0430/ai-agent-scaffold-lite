package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation;

import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.IDynamicTeamPlanValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.DynamicTeamPlanContextValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.DynamicTeamPlanWorkerValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.DynamicTeamPlanTaskValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.DynamicTeamPlanDependencyValidationRule;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationIssueVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按业务依赖显式执行计划校验规则。
 */
@Component
public class DynamicTeamPlanValidator {

    private final List<IDynamicTeamPlanValidationRule> rules;

    public DynamicTeamPlanValidator(
            DynamicTeamPlanContextValidationRule contextValidationRule,
            DynamicTeamPlanWorkerValidationRule workerValidationRule,
            DynamicTeamPlanTaskValidationRule taskValidationRule,
            DynamicTeamPlanDependencyValidationRule dependencyValidationRule) {
        this.rules = List.of(
                contextValidationRule,
                workerValidationRule,
                taskValidationRule,
                dependencyValidationRule
        );
    }

    public List<DynamicTeamPlanValidationIssueVO> validate(
            DynamicTeamPlanValidationCommandEntity command) {
        DynamicTeamPlanValidationContext context = new DynamicTeamPlanValidationContext(command);

        for (IDynamicTeamPlanValidationRule rule : rules) {
            rule.validate(context);
        }

        return List.copyOf(context.getIssues());
    }

}
