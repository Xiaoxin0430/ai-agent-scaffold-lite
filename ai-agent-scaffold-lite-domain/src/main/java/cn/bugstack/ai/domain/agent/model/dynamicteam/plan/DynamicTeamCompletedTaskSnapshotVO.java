package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 重新规划时仍然有效的已完成任务结果快照。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamCompletedTaskSnapshotVO {

    private String taskId;
    private String workerId;
    private String output;

}
