package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 已接受计划中的任务定义。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamTaskVO {

    private String taskId;
    private String workerId;
    private String title;
    private String description;
    private String expectedOutput;
    private List<String> acceptanceCriteria;
    private List<String> dependsOn;

}
