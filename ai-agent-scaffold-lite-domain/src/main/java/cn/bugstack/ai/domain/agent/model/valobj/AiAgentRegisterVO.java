package cn.bugstack.ai.domain.agent.model.valobj;

import com.google.adk.runner.InMemoryRunner;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ai Agent 智能体注册值对象
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/17 08:19
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiAgentRegisterVO {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 智能体ID
     */
    private String workflowId;

    /**
     * 智能体名称
     */
    private String workflowName;

    /**
     * 智能体描述
     */
    private String workflowDescription;

    /**
     * 智能体执行对象
     */
    private InMemoryRunner runner;

}
