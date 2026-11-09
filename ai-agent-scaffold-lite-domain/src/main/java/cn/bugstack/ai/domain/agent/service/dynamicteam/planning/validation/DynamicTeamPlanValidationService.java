package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationIssueVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationResultVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 动态团队计划校验门面。
 */
@Service
public class DynamicTeamPlanValidationService
        implements IDynamicTeamPlanValidationService {

    private final DynamicTeamPlanValidator planValidator;
    private final DynamicTeamPlanFactory planFactory;

    public DynamicTeamPlanValidationService(
            DynamicTeamPlanValidator planValidator,
            DynamicTeamPlanFactory planFactory) {
        this.planValidator = planValidator;
        this.planFactory = planFactory;
    }

    @Override
    public DynamicTeamPlanValidationResultVO validateAndBuild(
            DynamicTeamPlanValidationCommandEntity command) {
        List<DynamicTeamPlanValidationIssueVO> issues = planValidator.validate(command);
        if (!issues.isEmpty()) {
            return DynamicTeamPlanValidationResultVO.failure(issues);
        }

        DynamicTeamPlanVO acceptedPlan = planFactory.create(command);
        return DynamicTeamPlanValidationResultVO.success(acceptedPlan);
    }

}
