package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationIssueVO;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 校验任务内容、负责人和 Worker 使用情况，并建立任务索引。
 */
@Component
public class DynamicTeamPlanTaskValidationRule
        implements IDynamicTeamPlanValidationRule {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_TITLE_CHARACTERS = 200;
    private static final int MAX_DESCRIPTION_CHARACTERS = 4000;
    private static final int MAX_EXPECTED_OUTPUT_CHARACTERS = 2000;
    private static final int MAX_ACCEPTANCE_CRITERIA_COUNT = 20;
    private static final int MAX_ACCEPTANCE_CRITERION_CHARACTERS = 500;

    @Override
    public void validate(DynamicTeamPlanValidationContext context) {
        DynamicTeamPlanValidationCommandEntity command = context.getCommand();
        if (command == null || command.getPlanDraft() == null
                || command.getRuntimePolicy() == null) {
            return;
        }

        List<DynamicTeamPlanDraftVO.TaskDraft> tasks = command.getPlanDraft().getTasks();
        if (tasks == null || tasks.isEmpty()) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "TASKS_REQUIRED", "计划至少需要一个任务。", "planDraft.tasks"));
            return;
        }
        if (tasks.size() > command.getRuntimePolicy().getMaxTasks()) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "TASK_LIMIT_EXCEEDED",
                    "任务数量超过运行规则允许的上限。",
                    "planDraft.tasks"
            ));
        }

        for (int index = 0; index < tasks.size(); index++) {
            DynamicTeamPlanDraftVO.TaskDraft task = tasks.get(index);
            String path = "planDraft.tasks[" + index + "]";
            if (task == null) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                        "TASK_REQUIRED", "任务不能为空。", path));
                continue;
            }

            validateTaskId(task, path, context);
            validateWorkerReference(task, path, context);
            validateTaskFields(task, path, context);
        }

        validateWorkerUsage(context);
    }

    private void validateTaskId(
            DynamicTeamPlanDraftVO.TaskDraft task,
            String path,
            DynamicTeamPlanValidationContext context) {
        String taskId = task.getTaskId();
        if (blank(taskId) || !ID_PATTERN.matcher(taskId).matches()) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                    "TASK_ID_INVALID", "任务 ID 格式无效。",
                    path + ".taskId", taskId));
        } else if (context.getTasksById().containsKey(taskId)) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                    "TASK_ID_DUPLICATE", "任务 ID 重复。",
                    path + ".taskId", taskId));
        } else {
            context.getTasksById().put(taskId, task);
        }
    }

    private void validateWorkerReference(
            DynamicTeamPlanDraftVO.TaskDraft task,
            String path,
            DynamicTeamPlanValidationContext context) {
        if (blank(task.getWorkerId())
                || !context.getWorkersById().containsKey(task.getWorkerId())) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                    "TASK_WORKER_NOT_FOUND", "任务引用的 Worker 不存在。",
                    path + ".workerId", task.getTaskId()));
        }
    }

    private void validateTaskFields(
            DynamicTeamPlanDraftVO.TaskDraft task,
            String path,
            DynamicTeamPlanValidationContext context) {
        validateText(task.getTitle(), MAX_TITLE_CHARACTERS,
                "TASK_TITLE_INVALID", "任务标题不能为空或长度超过限制。",
                path + ".title", task.getTaskId(), context);
        validateText(task.getDescription(), MAX_DESCRIPTION_CHARACTERS,
                "TASK_DESCRIPTION_INVALID", "任务描述不能为空或长度超过限制。",
                path + ".description", task.getTaskId(), context);
        validateText(task.getExpectedOutput(), MAX_EXPECTED_OUTPUT_CHARACTERS,
                "TASK_EXPECTED_OUTPUT_INVALID", "任务预期输出不能为空或长度超过限制。",
                path + ".expectedOutput", task.getTaskId(), context);

        validateAcceptanceCriteria(task, path, context);
        if (task.getDependsOn() == null) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                    "TASK_DEPENDENCIES_REQUIRED",
                    "任务依赖必须是数组，没有依赖时使用空数组。",
                    path + ".dependsOn", task.getTaskId()));
        }
    }

    private void validateAcceptanceCriteria(
            DynamicTeamPlanDraftVO.TaskDraft task,
            String path,
            DynamicTeamPlanValidationContext context) {
        List<String> criteria = task.getAcceptanceCriteria();
        if (criteria == null || criteria.isEmpty()) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                    "ACCEPTANCE_CRITERIA_REQUIRED",
                    "任务至少需要一条验收标准。",
                    path + ".acceptanceCriteria", task.getTaskId()));
            return;
        }
        if (criteria.size() > MAX_ACCEPTANCE_CRITERIA_COUNT) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                    "ACCEPTANCE_CRITERIA_LIMIT_EXCEEDED",
                    "任务验收标准数量超过限制。",
                    path + ".acceptanceCriteria", task.getTaskId()));
        }

        Set<String> uniqueCriteria = new HashSet<>();
        for (int index = 0; index < criteria.size(); index++) {
            String criterion = criteria.get(index);
            String criterionPath = path + ".acceptanceCriteria[" + index + "]";
            if (blank(criterion) || criterion.length() > MAX_ACCEPTANCE_CRITERION_CHARACTERS) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                        "ACCEPTANCE_CRITERION_INVALID",
                        "任务验收标准不能为空或长度超过限制。",
                        criterionPath, task.getTaskId()));
                continue;
            }
            if (!uniqueCriteria.add(criterion.trim())) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                        "ACCEPTANCE_CRITERION_DUPLICATE",
                        "任务验收标准重复。",
                        criterionPath, task.getTaskId()));
            }
        }
    }

    private void validateWorkerUsage(DynamicTeamPlanValidationContext context) {
        Map<String, Integer> taskCountByWorker = new HashMap<>();
        for (String workerId : context.getWorkersById().keySet()) {
            taskCountByWorker.put(workerId, 0);
        }

        for (DynamicTeamPlanDraftVO.TaskDraft task : context.getTasksById().values()) {
            String workerId = task.getWorkerId();
            if (!taskCountByWorker.containsKey(workerId)) {
                continue;
            }
            int currentCount = taskCountByWorker.get(workerId);
            taskCountByWorker.put(workerId, currentCount + 1);
        }

        for (String workerId : taskCountByWorker.keySet()) {
            if (taskCountByWorker.get(workerId) == 0) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.worker(
                        "WORKER_HAS_NO_TASK", "Worker 没有负责任何任务。",
                        "planDraft.workers", workerId));
            }
        }
    }

    private void validateText(
            String value,
            int maxLength,
            String code,
            String message,
            String fieldPath,
            String taskId,
            DynamicTeamPlanValidationContext context) {
        if (blank(value) || value.length() > maxLength) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                    code, message, fieldPath, taskId));
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

}
