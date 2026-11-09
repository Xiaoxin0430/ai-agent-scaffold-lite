package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamWorkerAgentHandle;

/** 领域测试只需要句柄身份，无需构造 ADK Agent。 */
final class TestWorkerAgentHandle implements IDynamicTeamWorkerAgentHandle {
    private final String name;

    TestWorkerAgentHandle(String name) {
        this.name = name;
    }

    @Override
    public String getRuntimeAgentName() {
        return name;
    }

    @Override
    public void close() {
    }
}
