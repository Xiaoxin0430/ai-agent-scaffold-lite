package cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamTaskVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 在校验通过后创建带后端版本关系的正式计划。
 */
@Component
public class DynamicTeamPlanFactory {

    public DynamicTeamPlanVO create(DynamicTeamPlanValidationCommandEntity command) {
        DynamicTeamPlanDraftVO draft = command.getPlanDraft();
        DynamicTeamPlanVO previousPlan = command.getPreviousPlan();

        int planVersion = previousPlan == null
                ? 1
                : previousPlan.getPlanVersion() + 1;
        Integer previousPlanVersion = previousPlan == null
                ? null
                : previousPlan.getPlanVersion();

        return DynamicTeamPlanVO.builder()
                .runId(command.getRunId())
                .schemaVersion(draft.getSchemaVersion())
                .planVersion(planVersion)
                .previousPlanVersion(previousPlanVersion)
                .summary(draft.getSummary())
                .changeReason(draft.getChangeReason())
                .workers(convertWorkers(draft.getWorkers()))
                .tasks(convertTasks(draft.getTasks()))
                .build();
    }

    private List<DynamicTeamWorkerVO> convertWorkers(
            List<DynamicTeamPlanDraftVO.WorkerDraft> workerDrafts) {
        List<DynamicTeamWorkerVO> workers = new ArrayList<>();

        for (DynamicTeamPlanDraftVO.WorkerDraft workerDraft : workerDrafts) {
            workers.add(DynamicTeamWorkerVO.builder()
                    .workerId(workerDraft.getWorkerId())
                    .roleName(workerDraft.getRoleName())
                    .responsibility(workerDraft.getResponsibility())
                    .instruction(workerDraft.getInstruction())
                    .build());
        }

        return List.copyOf(workers);
    }

    private List<DynamicTeamTaskVO> convertTasks(
            List<DynamicTeamPlanDraftVO.TaskDraft> taskDrafts) {
        List<DynamicTeamTaskVO> tasks = new ArrayList<>();

        for (DynamicTeamPlanDraftVO.TaskDraft taskDraft : taskDrafts) {
            tasks.add(DynamicTeamTaskVO.builder()
                    .taskId(taskDraft.getTaskId())
                    .workerId(taskDraft.getWorkerId())
                    .title(taskDraft.getTitle())
                    .description(taskDraft.getDescription())
                    .expectedOutput(taskDraft.getExpectedOutput())
                    .acceptanceCriteria(List.copyOf(taskDraft.getAcceptanceCriteria()))
                    .dependsOn(List.copyOf(taskDraft.getDependsOn()))
                    .build());
        }

        return List.copyOf(tasks);
    }

}
