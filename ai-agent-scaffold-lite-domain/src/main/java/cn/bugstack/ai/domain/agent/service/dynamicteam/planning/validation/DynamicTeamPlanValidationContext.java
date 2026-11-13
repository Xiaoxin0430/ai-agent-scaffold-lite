package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationIssueVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次校验流水线共享的可变上下文，不跨运行复用。
 */
public class DynamicTeamPlanValidationContext {

    private final DynamicTeamPlanValidationCommandEntity command;
    private final List<DynamicTeamPlanValidationIssueVO> issues = new ArrayList<>();
    private final Map<String, DynamicTeamPlanDraftVO.WorkerDraft> workersById = new LinkedHashMap<>();
    private final Map<String, DynamicTeamPlanDraftVO.TaskDraft> tasksById = new LinkedHashMap<>();

    public DynamicTeamPlanValidationContext(DynamicTeamPlanValidationCommandEntity command) {
        this.command = command;
    }

    public DynamicTeamPlanValidationCommandEntity getCommand() {
        return command;
    }

    public List<DynamicTeamPlanValidationIssueVO> getIssues() {
        return issues;
    }

    public Map<String, DynamicTeamPlanDraftVO.WorkerDraft> getWorkersById() {
        return workersById;
    }

    public Map<String, DynamicTeamPlanDraftVO.TaskDraft> getTasksById() {
        return tasksById;
    }

    public void addIssue(DynamicTeamPlanValidationIssueVO issue) {
        issues.add(issue);
    }

}
