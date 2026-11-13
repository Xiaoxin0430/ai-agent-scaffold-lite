package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 动态团队运行规则，只能由服务端配置创建。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamRuntimePolicyVO {

    private boolean enabled;
    private int maxWorkers;
    private int maxTasks;
    private int maxConcurrentWorkers;
    private int maxReplans;
    private int stepTimeoutSeconds;
    private int runTimeoutSeconds;
    private int maxModelCalls;
    private int maxUserInputCharacters;
    /**
     * rag_search 允许检索的文档 ID 白名单；空表示未开放检索范围。
     */
    private List<String> ragDocumentIds;

}
