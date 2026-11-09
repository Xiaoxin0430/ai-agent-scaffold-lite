package cn.bugstack.ai.domain.agent.model.dynamicteam.execution;

import cn.bugstack.ai.domain.agent.service.dynamicteam.execution.IDynamicTeamTaskExecutionListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;

/** 一轮执行独占的取消句柄；模块 7 后续负责持有它。 */
public class DynamicTeamExecutionControlEntity {
    /** 该控制句柄只属于一轮执行，不能跨轮复用。 */
    private boolean cancelled;
    private boolean used;
    private DynamicTeamExecutionStopReasonEnum stopReason;
    private final long runDeadlineNanos;
    private final IDynamicTeamTaskExecutionListener taskExecutionListener;
    /** taskId 到正在运行 Future 的索引，取消时统一发出中断请求。 */
    private final Map<String, Future<?>> runningFutures = new HashMap<>();

    public DynamicTeamExecutionControlEntity() {
        this(Long.MAX_VALUE, progress -> { });
    }

    public DynamicTeamExecutionControlEntity(
            long runDeadlineNanos,
            IDynamicTeamTaskExecutionListener taskExecutionListener) {
        this.runDeadlineNanos = runDeadlineNanos;
        this.taskExecutionListener = taskExecutionListener == null
                ? progress -> { }
                : taskExecutionListener;
    }

    public synchronized void begin() {
        if (used) throw new IllegalStateException("执行控制句柄不能复用。");
        used = true;
    }

    public synchronized void register(String taskId, Future<?> future) {
        if (cancelled) {
            future.cancel(true);
        } else {
            runningFutures.put(taskId, future);
        }
    }

    public synchronized void unregister(String taskId) {
        runningFutures.remove(taskId);
    }

    /** 与 cancel 使用同一把锁，取消后不再提交；先提交的任务必定被登记并可取消。 */
    public synchronized <T> Future<T> submitIfActive(String taskId, CompletionService<T> completion,
            Callable<T> task) {
        if (cancelled) return null;
        Future<T> future = completion.submit(task);
        runningFutures.put(taskId, future);
        return future;
    }

    /** 将结果接纳和依赖解锁与取消串行化。回调不得阻塞或调用模型。 */
    public synchronized void acceptIfActive(Runnable acceptResult) {
        if (!cancelled) acceptResult.run();
    }

    public synchronized void cancel() {
        cancel(DynamicTeamExecutionStopReasonEnum.USER_CANCELLED);
    }

    public synchronized void cancel(
            DynamicTeamExecutionStopReasonEnum reason) {
        // 幂等取消：先改变门禁状态，再尝试中断已经登记的任务。
        if (cancelled) return;
        cancelled = true;
        stopReason = reason;
        for (Future<?> future : runningFutures.values()) future.cancel(true);
        runningFutures.clear();
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public synchronized DynamicTeamExecutionStopReasonEnum getStopReason() {
        return stopReason;
    }

    public boolean isRunTimedOut(long currentNanos) {
        return currentNanos >= runDeadlineNanos;
    }

    public void notifyTaskStatusChanged(
            DynamicTeamTaskExecutionProgressVO progress) {
        taskExecutionListener.onTaskStatusChanged(progress);
    }
}
