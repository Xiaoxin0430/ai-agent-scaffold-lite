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
        /**
         * 该 Worker 需要的服务端能力白名单项；缺省或空表示不装配工具。
         * 本阶段仅约定可出现 {@code rag_search}，具体合法性由校验步骤检查。
         */
        private List<String> neededCapabilities;
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
