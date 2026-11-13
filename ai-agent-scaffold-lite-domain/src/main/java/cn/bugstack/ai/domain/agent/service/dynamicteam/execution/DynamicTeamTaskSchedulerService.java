package cn.bugstack.ai.domain.agent.service.dynamicteam.execution;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamTaskExecutionPort;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.exception.DynamicTeamModelCallLimitException;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamExecutionStopReasonEnum;
import static cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskStatusEnum.*;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamExecutionControlEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionProgressVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionReportVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskRuntimeEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskStatusEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamTaskVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerPoolEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;

/** 单协调线程管理 DAG，执行线程只计算结果，不修改任务状态。 */
@Service
public class DynamicTeamTaskSchedulerService implements IDynamicTeamTaskSchedulerService {
    @Resource
    private IDynamicTeamTaskExecutionPort taskExecutionPort;

    @Override
    public DynamicTeamTaskExecutionReportVO execute(String userGoal, DynamicTeamPlanVO plan,
            DynamicTeamWorkerPoolEntity pool, DynamicTeamRuntimePolicyVO policy,
            DynamicTeamExecutionControlEntity control) {
        validate(userGoal, plan, pool, policy, control);

        RoundGraph graph = buildRoundGraph(plan, pool);
        Map<String, DynamicTeamTaskRuntimeEntity> tasks = graph.tasks();
        Map<String, List<String>> downstream = graph.downstream();
        ArrayDeque<String> ready = graph.ready();
        control.begin();
        int concurrency = policy.getMaxConcurrentWorkers();

        // 每一轮执行持有独立的有界线程池，线程数就是本轮 Worker 并发上限。
        ThreadPoolExecutor executor = new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(policy.getMaxTasks()), runnable -> {
                    Thread thread = new Thread(runnable, "dynamic-team-worker");
                    // 不能中断的 HTTP 请求不能阻止 JVM 退出；不代表网络资源已经释放。
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        ExecutorCompletionService<TaskOutcome> completion = new ExecutorCompletionService<>(executor);
        Map<Future<TaskOutcome>, String> running = new LinkedHashMap<>();
        Set<String> busyWorkers = new HashSet<>();
        try {
            while (!control.isCancelled()) {
                if (Thread.currentThread().isInterrupted()) {
                    control.cancel(DynamicTeamExecutionStopReasonEnum.INTERRUPTED);
                    break;
                }
                if (control.isRunTimedOut(System.nanoTime())) {
                    control.cancel(DynamicTeamExecutionStopReasonEnum.RUN_TIMEOUT);
                    break;
                }

                // 只扫描本轮开始时已有的候选数：忙 Worker 的任务放回队尾，
                // 继续寻找其他可运行任务，同时避免在同一轮无限旋转队列。
                int candidates = ready.size();
                while (candidates-- > 0 && running.size() < concurrency && !control.isCancelled()) {
                    String taskId = ready.remove();
                    DynamicTeamTaskRuntimeEntity runtime = tasks.get(taskId);
                    if (runtime.getStatus() != READY) continue;
                    //若workerid已有任务在跑，就放入队尾，继续寻找其他可运行任务
                    String workerId = runtime.getTask().getWorkerId();
                    if (busyWorkers.contains(workerId)) {
                        ready.add(taskId);
                        continue;
                    }

                    // 只传递当前任务在 dependsOn 中声明的直接上游结果，并在提交前固定快照。
                    Map<String, DynamicTeamTaskExecutionResultVO> inputs = new LinkedHashMap<>();
                    for (String dependency : runtime.getTask().getDependsOn()) {
                        inputs.put(dependency, tasks.get(dependency).getResult());
                    }
                    DynamicTeamWorkerRuntimeEntity worker = pool.getRequired(workerId);
                    Map<String, DynamicTeamTaskExecutionResultVO> snapshot = Collections.unmodifiableMap(inputs);
                    runtime.start();
                    notifyProgress(control, runtime, RUNNING, null);

                    // 提交和 Future 登记由 control 在同一把锁内完成，封住 cancel 与 submit 的竞态窗口。
                    Future<TaskOutcome> future = control.submitIfActive(taskId, completion, () -> {
                        try {
                            String output = taskExecutionPort.execute(userGoal, runtime.getTask(), worker, snapshot);
                            return new TaskOutcome(output, null, System.nanoTime());
                        } catch (Exception failure) {
                            return new TaskOutcome(null, failure, System.nanoTime());
                        }
                    });
                    if (future == null) break;
                    running.put(future, taskId);
                    busyWorkers.add(workerId);
                }
                if (control.isCancelled()) break;
                if (running.isEmpty()) {
                    // 合法 DAG 在没有运行任务时应全部进入终态；否则说明运行图已经无法推进。
                    for (DynamicTeamTaskRuntimeEntity runtime : tasks.values()) {
                        if (runtime.getResult() == null) throw new IllegalStateException("依赖图无法继续执行。");
                    }
                    break;
                }
                // 有界等待既接收完成通知，也定期检查取消与绝对调用期限。
                Future<TaskOutcome> completed = completion.poll(25, TimeUnit.MILLISECONDS);
                long now = System.nanoTime();
                if (completed == null && control.isRunTimedOut(now)) {
                    control.cancel(DynamicTeamExecutionStopReasonEnum.RUN_TIMEOUT);
                    break;
                }
                boolean timeout = false;
                for (Map.Entry<Future<TaskOutcome>, String> entry : running.entrySet()) {
                    // 已完成调用按其实际完成时间判定，不能用协调线程恢复执行的时间误判。
                    if (entry.getKey().isDone()) continue;
                    String taskId = entry.getValue();
                    DynamicTeamTaskRuntimeEntity runtime = tasks.get(taskId);
                    if (now - runtime.getStartedNanos() >= TimeUnit.SECONDS.toNanos(policy.getStepTimeoutSeconds())) {
                        runtime.finish(TIMED_OUT, null, "Worker 执行超过时间限制。");
                        notifyProgress(control, runtime, TIMED_OUT,
                                "Worker 执行超过时间限制。");
                        timeout = true;
                    }
                }
                if (timeout) {
                    control.cancel(DynamicTeamExecutionStopReasonEnum.MODEL_CALL_TIMEOUT);
                    break;
                }
                if (control.isCancelled()) break;
                if (completed == null) continue;
                String taskId = running.remove(completed);
                control.unregister(taskId);
                DynamicTeamTaskRuntimeEntity runtime = tasks.get(taskId);
                busyWorkers.remove(runtime.getTask().getWorkerId());
                try {
                    TaskOutcome outcome = completed.get();
                    if (control.isRunTimedOut(outcome.finishedNanos)) {
                        runtime.finish(TIMED_OUT, null, "整次团队运行超过时间限制。");
                        notifyProgress(control, runtime, TIMED_OUT,
                                "整次团队运行超过时间限制。");
                        control.cancel(DynamicTeamExecutionStopReasonEnum.RUN_TIMEOUT);
                        break;
                    }
                    if (hasModelCallLimitFailure(outcome.failure)) {
                        control.cancel(DynamicTeamExecutionStopReasonEnum.MODEL_CALL_LIMIT_REACHED);
                        break;
                    }
                    if (outcome.finishedNanos - runtime.getStartedNanos()
                            >= TimeUnit.SECONDS.toNanos(policy.getStepTimeoutSeconds())) {
                        runtime.finish(TIMED_OUT, null, "Worker 执行超过时间限制。");
                        notifyProgress(control, runtime, TIMED_OUT,
                                "Worker 执行超过时间限制。");
                        control.cancel(DynamicTeamExecutionStopReasonEnum.MODEL_CALL_TIMEOUT);
                        break;
                    }
                    if (outcome.failure != null || outcome.output == null || outcome.output.isBlank()) {
                        throw new IllegalStateException("调用失败或空输出");
                    }
                    control.acceptIfActive(() -> {
                        runtime.finish(SUCCEEDED, outcome.output, null);
                        notifyProgress(control, runtime, SUCCEEDED, null);

                        // 当前任务成功后，仅减少直接下游的剩余依赖数；减到 0 才进入 READY。
                        for (String childId : downstream.get(taskId)) {
                            DynamicTeamTaskRuntimeEntity child = tasks.get(childId);
                            child.dependencySucceeded();
                            if (child.getStatus() == READY) ready.add(childId);
                        }
                    });
                } catch (ExecutionException | IllegalStateException exception) {
                    control.acceptIfActive(() -> {
                        runtime.finish(FAILED, null, "Worker 执行失败或未返回有效结果。");
                        notifyProgress(control, runtime, FAILED,
                                "Worker 执行失败或未返回有效结果。");

                        // 必要上游失败后，所有直接和间接后代都无法继续，独立分支不受影响。
                        blockDescendants(taskId, downstream, tasks, control);
                    });
                }
            }
        } catch (InterruptedException exception) {
            control.cancel(DynamicTeamExecutionStopReasonEnum.INTERRUPTED);
            Thread.currentThread().interrupt();
        } finally {
            for (Map.Entry<Future<TaskOutcome>, String> entry : running.entrySet()) {
                entry.getKey().cancel(true);
                control.unregister(entry.getValue());
            }
            executor.shutdownNow();
        }

        return buildReport(tasks, control);
    }

    /** 建立本轮独占的任务状态、下游索引和初始就绪队列。 */
    private RoundGraph buildRoundGraph(DynamicTeamPlanVO plan, DynamicTeamWorkerPoolEntity pool) {
        Map<String, DynamicTeamTaskRuntimeEntity> tasks = new LinkedHashMap<>();
        Map<String, List<String>> downstream = new LinkedHashMap<>();
        ArrayDeque<String> ready = new ArrayDeque<>();

        for (DynamicTeamTaskVO task : plan.getTasks()) {
            if (task == null || task.getTaskId() == null || task.getDependsOn() == null
                    || tasks.containsKey(task.getTaskId())) throw new IllegalArgumentException("任务快照无效。");
            pool.getRequired(task.getWorkerId());
            DynamicTeamTaskRuntimeEntity runtime = new DynamicTeamTaskRuntimeEntity(task);
            tasks.put(task.getTaskId(), runtime);
            downstream.put(task.getTaskId(), new ArrayList<>());
            if (runtime.getStatus() == READY) ready.add(task.getTaskId());
        }

        // 只索引直接下游；任务成功后依赖计数减到零才可入队。
        for (DynamicTeamTaskVO task : plan.getTasks()) {
            for (String dependency : task.getDependsOn()) {
                if (!downstream.containsKey(dependency)) throw new IllegalArgumentException("上游任务不存在。");
                downstream.get(dependency).add(task.getTaskId());
            }
        }
        return new RoundGraph(tasks, downstream, ready);
    }

    /** 以正式计划顺序落盘；取消或超时后未完成任务统一变为 CANCELLED。 */
    private DynamicTeamTaskExecutionReportVO buildReport(
            Map<String, DynamicTeamTaskRuntimeEntity> tasks,
            DynamicTeamExecutionControlEntity control) {
        List<DynamicTeamTaskExecutionResultVO> results = new ArrayList<>();
        for (DynamicTeamTaskRuntimeEntity runtime : tasks.values()) {
            if (runtime.getResult() == null) {
                runtime.finish(CANCELLED, null, "本轮执行已停止。");
                notifyProgress(control, runtime, CANCELLED, "本轮执行已停止。");
            }
            results.add(runtime.getResult());
        }
        DynamicTeamExecutionStopReasonEnum stopReason = control.getStopReason();
        if (stopReason == null) {
            stopReason = DynamicTeamExecutionStopReasonEnum.ROUND_COMPLETED;
        }
        return DynamicTeamTaskExecutionReportVO.builder()
                .taskResults(List.copyOf(results))
                .stopReason(stopReason)
                .build();
    }

    private record RoundGraph(
            Map<String, DynamicTeamTaskRuntimeEntity> tasks,
            Map<String, List<String>> downstream,
            ArrayDeque<String> ready) {
    }

    private static class TaskOutcome {
        /** 执行线程记录真实完成时刻，避免协调线程调度延迟造成误判超时。 */
        private final String output;
        private final Exception failure;
        private final long finishedNanos;

        private TaskOutcome(String output, Exception failure, long finishedNanos) {
            this.output = output;
            this.failure = failure;
            this.finishedNanos = finishedNanos;
        }
    }

    private void blockDescendants(String failedId, Map<String, List<String>> downstream,
            Map<String, DynamicTeamTaskRuntimeEntity> tasks,
            DynamicTeamExecutionControlEntity control) {
        ArrayDeque<String> queue = new ArrayDeque<>(downstream.get(failedId));
        while (!queue.isEmpty()) {
            String taskId = queue.remove();
            DynamicTeamTaskRuntimeEntity task = tasks.get(taskId);
            if (task.getResult() != null) continue;
            task.finish(BLOCKED, null, "必要的上游任务失败。");
            notifyProgress(control, task, BLOCKED, "必要的上游任务失败。");
            queue.addAll(downstream.get(taskId));
        }
    }

    private boolean hasModelCallLimitFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof DynamicTeamModelCallLimitException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void notifyProgress(
            DynamicTeamExecutionControlEntity control,
            DynamicTeamTaskRuntimeEntity runtime,
            cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskStatusEnum status,
            String failureMessage) {
        control.notifyTaskStatusChanged(DynamicTeamTaskExecutionProgressVO.builder()
                .taskId(runtime.getTask().getTaskId())
                .workerId(runtime.getTask().getWorkerId())
                .status(status)
                .failureMessage(failureMessage)
                .build());
    }

    private void validate(String goal, DynamicTeamPlanVO plan, DynamicTeamWorkerPoolEntity pool,
            DynamicTeamRuntimePolicyVO policy, DynamicTeamExecutionControlEntity control) {
        if (goal == null || goal.isBlank() || plan == null || pool == null || policy == null || control == null) {
            throw new IllegalArgumentException("任务、计划、运行池、规则和控制句柄不能为空。");
        }
        if (!pool.getRunId().equals(plan.getRunId()) || pool.isClosed()
                || pool.getCurrentPlanVersion() != plan.getPlanVersion()) {
            throw new IllegalArgumentException("计划与 WorkerPool 归属或版本不一致。");
        }
        if (plan.getTasks() == null || plan.getTasks().isEmpty() || policy.getMaxTasks() < 1
                || plan.getTasks().size() > policy.getMaxTasks() || policy.getMaxConcurrentWorkers() < 1
                || policy.getStepTimeoutSeconds() < 1) {
            throw new IllegalArgumentException("任务数量、并发或超时配置无效。");
        }
    }
}
