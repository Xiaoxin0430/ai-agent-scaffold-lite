package cn.bugstack.ai.domain.agent.adapter.port.dynamicteam;

/** 临时 Worker 的模型实现句柄；领域层只使用名称和释放契约。 */
public interface IDynamicTeamWorkerAgentHandle extends AutoCloseable {

    String getRuntimeAgentName();

    @Override
    void close();
}
