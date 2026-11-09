package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 计划校验的互斥结果：问题列表或正式计划。
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamPlanValidationResultVO {

    private List<DynamicTeamPlanValidationIssueVO> issues;
    private DynamicTeamPlanVO acceptedPlan;

    public static DynamicTeamPlanValidationResultVO success(DynamicTeamPlanVO acceptedPlan) {
        if (acceptedPlan == null) {
            throw new IllegalArgumentException("acceptedPlan cannot be null");
        }
        return new DynamicTeamPlanValidationResultVO(List.of(), acceptedPlan);
    }

    public static DynamicTeamPlanValidationResultVO failure(
            List<DynamicTeamPlanValidationIssueVO> issues) {
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("issues cannot be empty");
        }
        return new DynamicTeamPlanValidationResultVO(List.copyOf(issues), null);
    }

    public boolean isValid() {
        return issues != null && issues.isEmpty() && acceptedPlan != null;
    }

}
