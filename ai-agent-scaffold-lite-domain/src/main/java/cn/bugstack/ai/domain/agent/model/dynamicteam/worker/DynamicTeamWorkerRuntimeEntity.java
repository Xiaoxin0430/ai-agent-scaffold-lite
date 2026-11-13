package cn.bugstack.ai.domain.agent.model.dynamicteam.worker;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;
import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamWorkerAgentHandle;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.List;

/**
 * 一次团队运行中的临时 Worker 运行句柄。
 */
@Getter
public class DynamicTeamWorkerRuntimeEntity implements AutoCloseable {

    private final String runId;
    private final int createdPlanVersion;
    private final DynamicTeamWorkerVO workerDefinition;
    private final String runtimeAgentName;

    @Getter(AccessLevel.NONE)
    private IDynamicTeamWorkerAgentHandle agentHandle;

    @Getter(AccessLevel.NONE)
    private boolean closed;

    public DynamicTeamWorkerRuntimeEntity(
            String runId,
            int createdPlanVersion,
            DynamicTeamWorkerVO workerDefinition,
            IDynamicTeamWorkerAgentHandle agentHandle) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空。");
        }
        if (createdPlanVersion <= 0) {
            throw new IllegalArgumentException("Worker 创建计划版本必须大于 0。");
        }
        if (workerDefinition == null
                || workerDefinition.getWorkerId() == null
                || workerDefinition.getWorkerId().isBlank()) {
            throw new IllegalArgumentException("Worker 定义和 workerId 不能为空。");
        }
        if (agentHandle == null) {
            throw new IllegalArgumentException("Worker Agent 不能为空。");
        }

        this.runId = runId;
        this.createdPlanVersion = createdPlanVersion;
        this.workerDefinition = copyWorker(workerDefinition);
        this.runtimeAgentName = agentHandle.getRuntimeAgentName();
        this.agentHandle = agentHandle;
    }

    public String getWorkerId() {
        return workerDefinition.getWorkerId();
    }

    /**
     * 只允许从尚未关闭的句柄获取执行对象。
     */
    public synchronized IDynamicTeamWorkerAgentHandle requireAgentHandle() {
        if (closed || agentHandle == null) {
            throw new IllegalStateException(
                    "临时 Worker 已释放，workerId=" + getWorkerId());
        }
        return agentHandle;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * 关闭实现句柄并清除强引用；底层请求是否响应中断由模型适配器决定。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        IDynamicTeamWorkerAgentHandle handle = agentHandle;
        agentHandle = null;
        closed = true;
        handle.close();
    }

    private DynamicTeamWorkerVO copyWorker(DynamicTeamWorkerVO worker) {
        List<String> capabilities = worker.getNeededCapabilities() == null
                ? List.of()
                : List.copyOf(worker.getNeededCapabilities());
        return DynamicTeamWorkerVO.builder()
                .workerId(worker.getWorkerId())
                .roleName(worker.getRoleName())
                .responsibility(worker.getResponsibility())
                .instruction(worker.getInstruction())
                .neededCapabilities(capabilities)
                .build();
    }

}
