package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 可定位到计划字段、Worker 或任务的一条校验问题。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamPlanValidationIssueVO {

    private String code;
    private String message;
    private String fieldPath;
    private String workerId;
    private String taskId;

    public static DynamicTeamPlanValidationIssueVO plan(
            String code,
            String message,
            String fieldPath) {
        return DynamicTeamPlanValidationIssueVO.builder()
                .code(code)
                .message(message)
                .fieldPath(fieldPath)
                .build();
    }

    public static DynamicTeamPlanValidationIssueVO worker(
            String code,
            String message,
            String fieldPath,
            String workerId) {
        return DynamicTeamPlanValidationIssueVO.builder()
                .code(code)
                .message(message)
                .fieldPath(fieldPath)
                .workerId(workerId)
                .build();
    }

    public static DynamicTeamPlanValidationIssueVO task(
            String code,
            String message,
            String fieldPath,
            String taskId) {
        return DynamicTeamPlanValidationIssueVO.builder()
                .code(code)
                .message(message)
                .fieldPath(fieldPath)
                .taskId(taskId)
                .build();
    }

}
