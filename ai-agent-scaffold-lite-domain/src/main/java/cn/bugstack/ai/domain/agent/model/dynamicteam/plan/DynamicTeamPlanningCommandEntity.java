package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 后端交给 Planner 的单次规划任务单。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamPlanningCommandEntity {

    private String runId;
    private DynamicTeamPlanningModeEnum mode;
    private String userGoal;
    private List<String> acceptanceRequirements;
    private DynamicTeamRuntimePolicyVO runtimePolicy;
    private Integer remainingModelCalls;
    private Long remainingRunSeconds;
    private DynamicTeamPlanVO previousPlan;
    private List<DynamicTeamCompletedTaskSnapshotVO> completedTasks;
    private List<DynamicTeamReviewIssueSnapshotVO> reviewIssues;

}
