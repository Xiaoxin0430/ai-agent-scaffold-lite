package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule;

import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidationContext;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationIssueVO;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 校验 Worker 定义并建立 Worker 索引。
 */
@Component
public class DynamicTeamPlanWorkerValidationRule
        implements IDynamicTeamPlanValidationRule {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_ROLE_NAME_CHARACTERS = 100;
    private static final int MAX_RESPONSIBILITY_CHARACTERS = 1000;
    private static final int MAX_INSTRUCTION_CHARACTERS = 4000;
    /** 本阶段服务端能力白名单；后续扩展 MCP 等时在此追加。 */
    private static final Set<String> ALLOWED_CAPABILITIES = Set.of("rag_search");

    @Override
    public void validate(DynamicTeamPlanValidationContext context) {
        DynamicTeamPlanValidationCommandEntity command = context.getCommand();
        if (command == null || command.getPlanDraft() == null
                || command.getRuntimePolicy() == null) {
            return;
        }

        List<DynamicTeamPlanDraftVO.WorkerDraft> workers =
                command.getPlanDraft().getWorkers();
        if (workers == null || workers.isEmpty()) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "WORKERS_REQUIRED", "计划至少需要一个 Worker。", "planDraft.workers"));
            return;
        }
        if (workers.size() > command.getRuntimePolicy().getMaxWorkers()) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "WORKER_LIMIT_EXCEEDED",
                    "Worker 数量超过运行规则允许的上限。",
                    "planDraft.workers"
            ));
        }

        for (int index = 0; index < workers.size(); index++) {
            DynamicTeamPlanDraftVO.WorkerDraft worker = workers.get(index);
            String path = "planDraft.workers[" + index + "]";
            if (worker == null) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                        "WORKER_REQUIRED", "Worker 不能为空。", path));
                continue;
            }

            String workerId = worker.getWorkerId();
            if (blank(workerId) || !ID_PATTERN.matcher(workerId).matches()) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.worker(
                        "WORKER_ID_INVALID", "Worker ID 格式无效。",
                        path + ".workerId", workerId));
            } else if (context.getWorkersById().containsKey(workerId)) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.worker(
                        "WORKER_ID_DUPLICATE", "Worker ID 重复。",
                        path + ".workerId", workerId));
            } else {
                context.getWorkersById().put(workerId, worker);
            }

            validateText(worker.getRoleName(), MAX_ROLE_NAME_CHARACTERS,
                    "WORKER_ROLE_NAME_INVALID", "Worker 角色名称不能为空或长度超过限制。",
                    path + ".roleName", workerId, context);
            validateText(worker.getResponsibility(), MAX_RESPONSIBILITY_CHARACTERS,
                    "WORKER_RESPONSIBILITY_INVALID", "Worker 职责不能为空或长度超过限制。",
                    path + ".responsibility", workerId, context);
            validateText(worker.getInstruction(), MAX_INSTRUCTION_CHARACTERS,
                    "WORKER_INSTRUCTION_INVALID", "Worker 工作说明不能为空或长度超过限制。",
                    path + ".instruction", workerId, context);
            validateCapabilities(
                    worker.getNeededCapabilities(),
                    path + ".neededCapabilities",
                    workerId,
                    context);
        }
    }

    /**
     * 校验能力列表：缺省或空合法；仅允许白名单项；空串与重复均拒绝。
     */
    private void validateCapabilities(
            List<String> neededCapabilities,
            String fieldPath,
            String workerId,
            DynamicTeamPlanValidationContext context) {
        if (neededCapabilities == null || neededCapabilities.isEmpty()) {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (int index = 0; index < neededCapabilities.size(); index++) {
            String capability = neededCapabilities.get(index);
            String itemPath = fieldPath + "[" + index + "]";
            if (blank(capability)) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.worker(
                        "CAPABILITY_INVALID",
                        "neededCapabilities 元素不能为空。",
                        itemPath,
                        workerId));
                continue;
            }
            if (!ALLOWED_CAPABILITIES.contains(capability)) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.worker(
                        "CAPABILITY_NOT_ALLOWED",
                        "不支持的 Worker 能力：" + capability + "。",
                        itemPath,
                        workerId));
                continue;
            }
            if (!seen.add(capability)) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.worker(
                        "CAPABILITY_DUPLICATE",
                        "neededCapabilities 中能力重复：" + capability + "。",
                        itemPath,
                        workerId));
            }
        }
    }

    private void validateText(
            String value,
            int maxLength,
            String code,
            String message,
            String fieldPath,
            String workerId,
            DynamicTeamPlanValidationContext context) {
        if (blank(value) || value.length() > maxLength) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.worker(
                    code, message, fieldPath, workerId));
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

}
