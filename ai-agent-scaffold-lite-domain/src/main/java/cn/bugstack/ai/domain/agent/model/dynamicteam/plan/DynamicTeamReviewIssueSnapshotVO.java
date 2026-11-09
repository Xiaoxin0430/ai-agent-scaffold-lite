package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 重新规划时提供给 Planner 的结构化评审问题快照。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamReviewIssueSnapshotVO {

    private String issueCode;
    private String taskId;
    private String message;

}
