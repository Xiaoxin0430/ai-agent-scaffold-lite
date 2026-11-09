package cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamWorkerAgentHandle;
import com.google.adk.agents.LlmAgent;

/** ADK Worker 的运行期强引用；关闭时仅释放引用，不声称终止模型请求。 */
public class DynamicTeamAdkWorkerAgentHandle implements IDynamicTeamWorkerAgentHandle {

    private final String runtimeAgentName;
    private LlmAgent agent;

    public DynamicTeamAdkWorkerAgentHandle(LlmAgent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("Worker Agent 不能为空。");
        }
        this.runtimeAgentName = agent.name();
        this.agent = agent;
    }

    @Override
    public String getRuntimeAgentName() {
        return runtimeAgentName;
    }

    public synchronized LlmAgent requireAgent() {
        if (agent == null) {
            throw new IllegalStateException("临时 Worker Agent 已释放。");
        }
        return agent;
    }

    @Override
    public synchronized void close() {
        agent = null;
    }
}
