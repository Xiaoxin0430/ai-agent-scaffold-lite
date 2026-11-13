package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule;

import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidationContext;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationIssueVO;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验任务依赖引用并使用拓扑排序检测循环依赖。
 */
@Component
public class DynamicTeamPlanDependencyValidationRule
        implements IDynamicTeamPlanValidationRule {

    @Override
    public void validate(DynamicTeamPlanValidationContext context) {
        if (context.getTasksById().isEmpty()) {
            return;
        }

        Map<String, Integer> inDegrees = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (String taskId : context.getTasksById().keySet()) {
            inDegrees.put(taskId, 0);
            outgoing.put(taskId, new ArrayList<>());
        }

        for (DynamicTeamPlanDraftVO.TaskDraft task : context.getTasksById().values()) {
            validateTaskDependencies(task, context, inDegrees, outgoing);
        }

        detectCycle(context, inDegrees, outgoing);
    }

    private void validateTaskDependencies(
            DynamicTeamPlanDraftVO.TaskDraft task,
            DynamicTeamPlanValidationContext context,
            Map<String, Integer> inDegrees,
            Map<String, List<String>> outgoing) {

        List<String> dependencies = task.getDependsOn();
        if (dependencies == null) {
            return;
        }

        Set<String> uniqueDependencies = new HashSet<>();
        for (int index = 0; index < dependencies.size(); index++) {
            String dependencyId = dependencies.get(index);
            String fieldPath = "planDraft.tasks." + task.getTaskId()
                    + ".dependsOn[" + index + "]";

            if (blank(dependencyId)) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                        "DEPENDENCY_ID_REQUIRED", "依赖任务 ID 不能为空。",
                        fieldPath, task.getTaskId()));
                continue;
            }
            if (!uniqueDependencies.add(dependencyId)) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                        "DEPENDENCY_DUPLICATE", "同一个依赖任务不能重复出现。",
                        fieldPath, task.getTaskId()));
                continue;
            }
            if (task.getTaskId().equals(dependencyId)) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                        "TASK_SELF_DEPENDENCY", "任务不能依赖自己。",
                        fieldPath, task.getTaskId()));
                continue;
            }
            if (!context.getTasksById().containsKey(dependencyId)) {
                context.addIssue(DynamicTeamPlanValidationIssueVO.task(
                        "DEPENDENCY_NOT_FOUND", "依赖的任务不存在：" + dependencyId,
                        fieldPath, task.getTaskId()));
                continue;
            }

            outgoing.get(dependencyId).add(task.getTaskId());
            int currentInDegree = inDegrees.get(task.getTaskId());
            inDegrees.put(task.getTaskId(), currentInDegree + 1);
        }
    }

    private void detectCycle(
            DynamicTeamPlanValidationContext context,
            Map<String, Integer> inDegrees,
            Map<String, List<String>> outgoing) {
        ArrayDeque<String> readyTasks = new ArrayDeque<>();
        for (String taskId : inDegrees.keySet()) {
            if (inDegrees.get(taskId) == 0) {
                readyTasks.add(taskId);
            }
        }

        int processedTaskCount = 0;
        while (!readyTasks.isEmpty()) {
            String completedTaskId = readyTasks.remove();
            processedTaskCount++;

            for (String nextTaskId : outgoing.get(completedTaskId)) {
                int remainingDependencies = inDegrees.get(nextTaskId) - 1;
                inDegrees.put(nextTaskId, remainingDependencies);
                if (remainingDependencies == 0) {
                    readyTasks.add(nextTaskId);
                }
            }
        }

        if (processedTaskCount != context.getTasksById().size()) {
            context.addIssue(DynamicTeamPlanValidationIssueVO.plan(
                    "TASK_DEPENDENCY_CYCLE",
                    "任务依赖中存在循环，计划无法执行。",
                    "planDraft.tasks"
            ));
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

}
