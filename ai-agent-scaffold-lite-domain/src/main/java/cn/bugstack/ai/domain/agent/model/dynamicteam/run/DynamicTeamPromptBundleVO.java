package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 一套配套的动态团队固定提示词。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamPromptBundleVO {

    private String version;
    private String plannerInstruction;
    private String reviewerInstruction;
    private String summarizerInstruction;
    private String workerInstruction;

}
