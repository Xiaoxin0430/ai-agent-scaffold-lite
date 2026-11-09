package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningModeEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationIssueVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import org.springframework.stereotype.Component;

/**
 * 校验命令、协议和重新规划上下文。
 */
@Component
public class DynamicTeamPlanContextValidationRule
        implements IDynamicTeamPlanValidationRule {

    private static final int MAX_SUMMARY_CHARACTERS = 1000;
    private static final int MAX_CHANGE_REASON_CHARACTERS = 1000;

    @Override
    public void validate(DynamicTeamPlanValidationContext context) {
        DynamicTeamPlanValidationCommandEntity command = context.getCommand();

        if (command == null) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "VALIDATION_COMMAND_REQUIRED",
                    "计划校验命令不能为空。",
                    null
            ));
            return;
        }

        if (blank(command.getRunId())) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "RUN_ID_REQUIRED", "运行标识不能为空。", "runId"));
        }
        if (command.getPlanningMode() == null) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "PLANNING_MODE_REQUIRED", "规划模式不能为空。", "planningMode"));
        }
        if (command.getPlanDraft() == null) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "PLAN_DRAFT_REQUIRED", "计划草稿不能为空。", "planDraft"));
        }
        if (command.getRuntimePolicy() == null) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "RUNTIME_POLICY_REQUIRED", "运行规则不能为空。", "runtimePolicy"));
        }

        validateDraftMetadata(command.getPlanDraft(), context);
        validatePlanningMode(command, context);
    }

    private void validateDraftMetadata(
            DynamicTeamPlanDraftVO draft,
            DynamicTeamPlanValidationContext context) {
        if (draft == null) {
            return;
        }
        if (!"1.0".equals(draft.getSchemaVersion())) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "SCHEMA_VERSION_UNSUPPORTED",
                    "仅支持 1.0 版本的团队计划协议。",
                    "planDraft.schemaVersion"
            ));
        }
        validateText(draft.getSummary(), MAX_SUMMARY_CHARACTERS,
                "PLAN_SUMMARY_INVALID", "计划说明不能为空或长度超过限制。",
                "planDraft.summary", context);
        validateText(draft.getChangeReason(), MAX_CHANGE_REASON_CHARACTERS,
                "CHANGE_REASON_INVALID", "计划生成或变更原因不能为空或长度超过限制。",
                "planDraft.changeReason", context);
    }

    private void validatePlanningMode(
            DynamicTeamPlanValidationCommandEntity command,
            DynamicTeamPlanValidationContext context) {
        DynamicTeamPlanningModeEnum mode = command.getPlanningMode();
        if (mode == null) {
            return;
        }

        DynamicTeamPlanVO previousPlan = command.getPreviousPlan();
        if (DynamicTeamPlanningModeEnum.INITIAL.equals(mode)) {
            if (previousPlan != null) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                        "INITIAL_PLAN_HAS_PREVIOUS_PLAN",
                        "初始规划不能携带上一版计划。",
                        "previousPlan"
                ));
            }
            return;
        }

        if (previousPlan == null) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "PREVIOUS_PLAN_REQUIRED",
                    "重新规划必须携带上一版计划。",
                    "previousPlan"
            ));
            return;
        }

        if (!safeEquals(command.getRunId(), previousPlan.getRunId())) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "PREVIOUS_PLAN_RUN_MISMATCH",
                    "上一版计划不属于当前运行。",
                    "previousPlan.runId"
            ));
        }

        if (previousPlan.getPlanVersion() < 1) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "PREVIOUS_PLAN_VERSION_INVALID",
                    "上一版计划版本必须大于零。",
                    "previousPlan.planVersion"
            ));
            return;
        }

        DynamicTeamRuntimePolicyVO policy = command.getRuntimePolicy();
        if (policy == null) {
            return;
        }

        int completedReplans = previousPlan.getPlanVersion() - 1;
        if (completedReplans >= policy.getMaxReplans()) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "REPLAN_LIMIT_EXCEEDED",
                    "重新规划次数已经达到上限。",
                    "previousPlan.planVersion"
            ));
        }
    }

    private void validateText(
            String value,
            int maxLength,
            String code,
            String message,
            String fieldPath,
            DynamicTeamPlanValidationContext context) {
        if (blank(value) || value.length() > maxLength) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    code, message, fieldPath));
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean safeEquals(String left, String right) {
        return left != null && left.equals(right);
    }

}
