package cn.bugstack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态团队 V1 服务端运行配置。
 */
@Data
@ConfigurationProperties(prefix = "ai.agent.dynamic-team")
public class DynamicTeamProperties {

    private boolean enabled = false;
    private int maxWorkers = 5;
    private int maxTasks = 20;
    private int maxConcurrentWorkers = 3;
    private int maxReplans = 2;
    private int stepTimeoutSeconds = 600;
    private int runTimeoutSeconds = 1200;
    private int maxModelCalls = 25;
    private int maxUserInputCharacters = 20000;
    /**
     * 动态团队 rag_search 可检索的文档 ID；默认项目架构说明语料。
     */
    private List<String> ragDocumentIds = new ArrayList<>(List.of("project-architecture"));

}
