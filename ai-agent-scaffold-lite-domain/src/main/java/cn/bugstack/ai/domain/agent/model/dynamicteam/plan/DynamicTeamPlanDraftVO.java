package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Planner 输出的未校验计划草稿。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamPlanDraftVO {

    private String schemaVersion;
    private String summary;
    private String changeReason;
    private List<WorkerDraft> workers;
    private List<TaskDraft> tasks;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkerDraft {
        private String workerId;
        private String roleName;
        private String responsibility;
        private String instruction;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskDraft {
        private String taskId;
        private String workerId;
        private String title;
        private String description;
        private String expectedOutput;
        private List<String> acceptanceCriteria;
        private List<String> dependsOn;
    }

}
