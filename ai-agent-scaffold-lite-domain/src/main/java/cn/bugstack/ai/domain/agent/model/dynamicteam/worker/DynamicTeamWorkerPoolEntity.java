package cn.bugstack.ai.domain.agent.model.dynamicteam.worker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个 runId 专属的临时 Worker 运行池。
 */
public class DynamicTeamWorkerPoolEntity implements AutoCloseable {

    private final String runId;
    private int currentPlanVersion;
    private final Map<String, DynamicTeamWorkerRuntimeEntity> workers =
            new LinkedHashMap<>();
    private boolean closed;

    public DynamicTeamWorkerPoolEntity(String runId, int currentPlanVersion) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空。");
        }
        if (currentPlanVersion <= 0) {
            throw new IllegalArgumentException("当前计划版本必须大于 0。");
        }
        this.runId = runId;
        this.currentPlanVersion = currentPlanVersion;
    }

    public synchronized void add(DynamicTeamWorkerRuntimeEntity runtime) {
        requireOpen();
        if (runtime == null) {
            throw new IllegalArgumentException("Worker 运行句柄不能为空。");
        }
        if (!runId.equals(runtime.getRunId())) {
            throw new IllegalArgumentException("Worker 不属于当前运行。");
        }

        String workerId = runtime.getWorkerId();
        if (workers.containsKey(workerId)) {
            throw new IllegalStateException("Worker 已存在，workerId=" + workerId);
        }
        workers.put(workerId, runtime);
    }

    public synchronized DynamicTeamWorkerRuntimeEntity getRequired(String workerId) {
        requireOpen();
        DynamicTeamWorkerRuntimeEntity runtime = workers.get(workerId);
        if (runtime == null) {
            throw new IllegalStateException("Worker 不存在，workerId=" + workerId);
        }
        return runtime;
    }

    public void removeAndClose(String workerId) {
        DynamicTeamWorkerRuntimeEntity removed;
        synchronized (this) {
            requireOpen();
            removed = workers.remove(workerId);
            if (removed == null) {
                throw new IllegalStateException("Worker 不存在，workerId=" + workerId);
            }
        }
        removed.close();
    }

    public synchronized boolean contains(String workerId) {
        requireOpen();
        return workers.containsKey(workerId);
    }

    public synchronized int size() {
        requireOpen();
        return workers.size();
    }

    public synchronized List<DynamicTeamWorkerRuntimeEntity> snapshot() {
        requireOpen();
        return List.copyOf(workers.values());
    }

    public synchronized String getRunId() {
        return runId;
    }

    public synchronized int getCurrentPlanVersion() {
        return currentPlanVersion;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized void commitPlanVersion(int targetPlanVersion) {
        requireOpen();
        if (targetPlanVersion != currentPlanVersion + 1) {
            throw new IllegalStateException(
                    "计划版本必须连续递增，current=" + currentPlanVersion
                            + "，target=" + targetPlanVersion);
        }
        currentPlanVersion = targetPlanVersion;
    }

    @Override
    public void close() {
        List<DynamicTeamWorkerRuntimeEntity> runtimes;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            runtimes = new ArrayList<>(workers.values());
            workers.clear();
        }

        RuntimeException cleanupFailure = null;
        for (DynamicTeamWorkerRuntimeEntity runtime : runtimes) {
            try {
                runtime.close();
            } catch (RuntimeException exception) {
                if (cleanupFailure == null) {
                    cleanupFailure = new IllegalStateException(
                            "WorkerPool 清理不完整，runId=" + runId);
                }
                cleanupFailure.addSuppressed(exception);
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("WorkerPool 已关闭，runId=" + runId);
        }
    }

}
