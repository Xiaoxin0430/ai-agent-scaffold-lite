package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamRunEventPublisher;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamExecutionControlEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerPoolEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionProgressVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

/**
 * 一次动态团队运行的权威上下文。
 * 复杂模型调用和任务调度仍由领域服务完成，本聚合只保护运行事实和状态约束。
 */
public class DynamicTeamRunContextAggregate {

    private final String runId;
    private final String userGoal;
    private final List<String> acceptanceRequirements;
    private final DynamicTeamRuntimePolicyVO runtimePolicy;
    private final DynamicTeamModelCallBudgetEntity modelCallBudget;
    private final Instant startedAt;
    private final long deadlineNanos;
    private final Map<Integer, DynamicTeamPlanVO> planHistory = new LinkedHashMap<>();
    private final Map<Integer, List<DynamicTeamTaskExecutionResultVO>> taskResults =
            new LinkedHashMap<>();
    private final List<DynamicTeamRunEventVO> events = new ArrayList<>();
    private final IDynamicTeamRunEventPublisher eventPublisher;

    private DynamicTeamRunStatusEnum status;
    private DynamicTeamRunTerminationReasonEnum terminationReason;
    private DynamicTeamWorkerPoolEntity workerPool;
    /** 只在全部任务成功并完成汇总后写入；其余终态保持 null，不返回虚假汇总。 */
    private String finalAnswer;
    private DynamicTeamExecutionControlEntity executionControl;
    private Future<?> activeControlCallFuture;
    private boolean cancellationRequested;
    private long nextEventSequence = 1L;
    private boolean resourcesReleased;

    private DynamicTeamRunContextAggregate(
            String runId,
            String userGoal,
            List<String> acceptanceRequirements,
            DynamicTeamRuntimePolicyVO runtimePolicy,
            Instant startedAt,
            long startedNanos,
            IDynamicTeamRunEventPublisher eventPublisher) {
        this.runId = runId;
        this.userGoal = userGoal;
        this.acceptanceRequirements = List.copyOf(acceptanceRequirements);
        this.runtimePolicy = runtimePolicy;
        this.modelCallBudget = new DynamicTeamModelCallBudgetEntity(
                runtimePolicy.getMaxModelCalls());
        this.startedAt = startedAt;
        this.eventPublisher = eventPublisher;
        this.deadlineNanos = startedNanos
                + TimeUnit.SECONDS.toNanos(runtimePolicy.getRunTimeoutSeconds());
        this.status = DynamicTeamRunStatusEnum.PLANNING;
        appendEvent(DynamicTeamRunEventTypeEnum.RUN_STARTED,
                null, null, null, null, null, startedAt);
    }

    public static DynamicTeamRunContextAggregate start(
            String runId,
            String userGoal,
            List<String> acceptanceRequirements,
            DynamicTeamRuntimePolicyVO runtimePolicy,
            Instant startedAt,
            long startedNanos) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空。");
        }
        if (userGoal == null || userGoal.isBlank()) {
            throw new IllegalArgumentException("用户目标不能为空。");
        }
        if (acceptanceRequirements == null) {
            throw new IllegalArgumentException("验收要求不能为空。");
        }
        if (runtimePolicy == null || runtimePolicy.getMaxModelCalls() <= 0
                || runtimePolicy.getRunTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("运行规则无效。");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("运行开始时间不能为空。");
        }
        return new DynamicTeamRunContextAggregate(
                runId, userGoal, acceptanceRequirements,
                runtimePolicy, startedAt, startedNanos,
                IDynamicTeamRunEventPublisher.NO_OP);
    }

    public static DynamicTeamRunContextAggregate start(
            String runId,
            String userGoal,
            List<String> acceptanceRequirements,
            DynamicTeamRuntimePolicyVO runtimePolicy,
            Instant startedAt,
            long startedNanos,
            IDynamicTeamRunEventPublisher eventPublisher) {

        if (eventPublisher == null) {
            throw new IllegalArgumentException("事件发布器不能为空。");
        }
        if (runId == null || runId.isBlank() || userGoal == null || userGoal.isBlank()
                || acceptanceRequirements == null || runtimePolicy == null || startedAt == null) {
            throw new IllegalArgumentException("运行启动参数不完整。");
        }
        return new DynamicTeamRunContextAggregate(
                runId, userGoal, acceptanceRequirements,
                runtimePolicy, startedAt, startedNanos, eventPublisher);
    }

    public synchronized void transitionTo(
            DynamicTeamRunStatusEnum targetStatus,
            Instant occurredAt) {
        requireActive();
        if (targetStatus == null || targetStatus.isTerminal()) {
            throw new IllegalArgumentException("普通状态流转不能直接写入终态。");
        }
        if (!isAllowedTransition(status, targetStatus)) {
            throw new IllegalStateException(
                    "不允许的运行状态流转，current=" + status
                            + "，target=" + targetStatus);
        }

        status = targetStatus;
        appendEvent(DynamicTeamRunEventTypeEnum.RUN_STATUS_CHANGED,
                currentPlanVersion(), null, null, null, null, occurredAt);
    }

    public synchronized int reserveModelCall() {
        requireActive();
        return modelCallBudget.reserveModelCall();
    }

    public synchronized long remainingRunSeconds(long currentNanos) {
        return TimeUnit.NANOSECONDS.toSeconds(
                remainingRunNanos(currentNanos));
    }

    public synchronized long remainingRunNanos(long currentNanos) {

        return Math.max(0L, deadlineNanos - currentNanos);
    }

    public synchronized boolean isRunTimedOut(long currentNanos) {

        return currentNanos >= deadlineNanos;
    }

    public synchronized void recordPlan(
            DynamicTeamPlanVO plan,
            Instant occurredAt) {
        requireActive();
        if (plan == null || !runId.equals(plan.getRunId())
                || plan.getPlanVersion() <= 0) {
            throw new IllegalArgumentException("正式计划不属于当前运行。");
        }
        if (!planHistory.isEmpty()
                && plan.getPlanVersion() != currentPlanVersion() + 1) {
            throw new IllegalStateException("计划版本必须连续递增。");
        }

        planHistory.put(plan.getPlanVersion(), plan);
        appendEvent(DynamicTeamRunEventTypeEnum.PLAN_ACCEPTED,
                plan.getPlanVersion(), null, null, null, null, occurredAt);
    }

    public synchronized void attachWorkerPool(
            DynamicTeamWorkerPoolEntity pool,
            Instant occurredAt) {
        requireActive();
        if (pool == null || !runId.equals(pool.getRunId())
                || pool.getCurrentPlanVersion() != currentPlanVersion()) {
            throw new IllegalArgumentException("WorkerPool 不属于当前计划版本。");
        }
        this.workerPool = pool;
        this.resourcesReleased = false;
        appendEvent(DynamicTeamRunEventTypeEnum.TEAM_CREATED,
                currentPlanVersion(), null, null, null, null, occurredAt);
    }

    public synchronized void attachExecutionControl(
            DynamicTeamExecutionControlEntity control) {
        requireActive();
        if (control == null) {
            throw new IllegalArgumentException("执行控制句柄不能为空。");
        }
        this.executionControl = control;
    }

    public synchronized void clearExecutionControl(
            DynamicTeamExecutionControlEntity control) {
        if (this.executionControl == control) {
            this.executionControl = null;
        }
    }

    public synchronized void attachControlCallFuture(Future<?> future) {
        requireActive();
        activeControlCallFuture = future;
        if (cancellationRequested && future != null) {
            future.cancel(true);
        }
    }

    public synchronized void clearControlCallFuture(Future<?> future) {
        if (activeControlCallFuture == future) {
            activeControlCallFuture = null;
        }
    }

    public void requestCancellation() {
        DynamicTeamExecutionControlEntity control;
        Future<?> controlCallFuture;
        synchronized (this) {
            cancellationRequested = true;
            control = executionControl;
            controlCallFuture = activeControlCallFuture;
        }
        // 不持有运行上下文锁执行取消，避免和结果回调形成锁顺序反转。
        if (controlCallFuture != null) {
            controlCallFuture.cancel(true);
        }
        if (control != null) {
            control.cancel();
        }
    }

    public synchronized boolean isCancellationRequested() {
        return cancellationRequested;
    }

    public synchronized void recordTaskProgress(
            DynamicTeamTaskExecutionProgressVO progress,
            Instant occurredAt) {
        // 取消或超时后的迟到回调不能改变终态，也不能污染事件顺序。
        if (status.isTerminal()) {
            return;
        }
        if (progress == null || progress.getStatus() == null) {
            throw new IllegalArgumentException("任务进度不能为空。");
        }
        appendEvent(DynamicTeamRunEventTypeEnum.TASK_STATUS_CHANGED,
                currentPlanVersion(), progress.getWorkerId(), progress.getTaskId(),
                progress.getStatus().name(), progress.getFailureMessage(), occurredAt);
    }

    public synchronized void recordTaskResults(
            int planVersion,
            List<DynamicTeamTaskExecutionResultVO> results) {
        requireActive();
        if (!planHistory.containsKey(planVersion) || results == null) {
            throw new IllegalArgumentException("任务结果缺少对应计划版本。");
        }
        taskResults.put(planVersion, List.copyOf(results));
    }

    /**
     * 原子提交最终回答和完成终态。取消请求先到时拒绝提交，避免出现
     * CANCELLED 状态却携带 finalAnswer 的矛盾快照。
     */
    public synchronized boolean completeWithFinalAnswer(String answer, Instant occurredAt) {
        requireActive();
        if (status != DynamicTeamRunStatusEnum.SUMMARIZING) {
            throw new IllegalStateException("只有 SUMMARIZING 阶段可以写入最终回答，当前状态=" + status);
        }
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("最终回答不能为空。");
        }
        if (cancellationRequested) {
            return false;
        }
        this.finalAnswer = answer;
        appendEvent(DynamicTeamRunEventTypeEnum.SUMMARIZING_COMPLETED,
                currentPlanVersion(), null, null, null, null, occurredAt);
        status = DynamicTeamRunStatusEnum.COMPLETED;
        terminationReason = DynamicTeamRunTerminationReasonEnum.NORMAL_COMPLETION;
        appendEvent(DynamicTeamRunEventTypeEnum.RUN_TERMINATED,
                currentPlanVersion(), null, null,
                DynamicTeamRunTerminationReasonEnum.NORMAL_COMPLETION.name(),
                null, occurredAt);
        return true;
    }

    public synchronized void terminate(
            DynamicTeamRunStatusEnum terminalStatus,
            DynamicTeamRunTerminationReasonEnum reason,
            Instant occurredAt) {
        if (status.isTerminal()) {
            return;
        }
        if (terminalStatus == null || !terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("终止运行必须写入终态。");
        }
        if (reason == null) {
            throw new IllegalArgumentException("运行终止原因不能为空。");
        }

        status = terminalStatus;
        terminationReason = reason;
        appendEvent(DynamicTeamRunEventTypeEnum.RUN_TERMINATED,
                currentPlanVersion(), null, null, reason.name(), null, occurredAt);
    }

    public synchronized void markResourcesReleased() {
        workerPool = null;
        executionControl = null;
        activeControlCallFuture = null;
        resourcesReleased = true;
    }

    public synchronized DynamicTeamRunSnapshotVO snapshot() {
        DynamicTeamPlanVO currentPlan = currentPlan();
        List<DynamicTeamTaskExecutionResultVO> currentResults = currentPlan == null
                ? List.of()
                : taskResults.getOrDefault(currentPlan.getPlanVersion(), List.of());
        return DynamicTeamRunSnapshotVO.builder()
                .runId(runId)
                .userGoal(userGoal)
                .acceptanceRequirements(List.copyOf(acceptanceRequirements))
                .status(status)
                .terminationReason(terminationReason)
                .planHistory(List.copyOf(planHistory.values()))
                .currentPlan(currentPlan)
                .currentTaskResults(List.copyOf(currentResults))
                .usedModelCalls(modelCallBudget.getUsedModelCalls())
                .remainingModelCalls(modelCallBudget.getRemainingModelCalls())
                .startedAt(startedAt)
                .resourcesReleased(resourcesReleased)
                .events(List.copyOf(events))
                .finalAnswer(finalAnswer)
                .build();
    }

    public synchronized DynamicTeamRunStatusEnum getStatus() {
        return status;
    }

    public String getRunId() {
        return runId;
    }

    public String getUserGoal() {
        return userGoal;
    }

    public List<String> getAcceptanceRequirements() {
        return acceptanceRequirements;
    }

    public DynamicTeamRuntimePolicyVO getRuntimePolicy() {
        return runtimePolicy;
    }

    public DynamicTeamModelCallBudgetEntity getModelCallBudget() {
        return modelCallBudget;
    }

    public long getDeadlineNanos() {
        return deadlineNanos;
    }

    public synchronized DynamicTeamWorkerPoolEntity getRequiredWorkerPool() {
        if (workerPool == null) {
            throw new IllegalStateException("当前运行没有可用的 WorkerPool。");
        }
        return workerPool;
    }

    public synchronized boolean hasWorkerPool() {
        return workerPool != null;
    }

    private void appendEvent(
            DynamicTeamRunEventTypeEnum eventType,
            Integer planVersion,
            String workerId,
            String taskId,
            String reasonCode,
            String message,
            Instant occurredAt) {
        DynamicTeamRunEventVO event = DynamicTeamRunEventVO.builder()
                .sequence(nextEventSequence++)
                .runId(runId)
                .planVersion(planVersion)
                .eventType(eventType)
                .runStatus(status)
                .workerId(workerId)
                .taskId(taskId)
                .reasonCode(reasonCode)
                .message(message)
                .occurredAt(occurredAt)
                .build();
        events.add(event);
        eventPublisher.publish(event);
    }

    private int currentPlanVersion() {
        DynamicTeamPlanVO plan = currentPlan();
        return plan == null ? 0 : plan.getPlanVersion();
    }

    private DynamicTeamPlanVO currentPlan() {
        DynamicTeamPlanVO current = null;
        for (DynamicTeamPlanVO plan : planHistory.values()) {
            current = plan;
        }
        return current;
    }

    private void requireActive() {
        if (status.isTerminal()) {
            throw new IllegalStateException("运行已经结束，不能继续修改。");
        }
    }

    private boolean isAllowedTransition(
            DynamicTeamRunStatusEnum current,
            DynamicTeamRunStatusEnum target) {
        return switch (current) {
            case PLANNING -> target == DynamicTeamRunStatusEnum.VALIDATING;
            case VALIDATING -> target == DynamicTeamRunStatusEnum.CREATING;
            case CREATING -> target == DynamicTeamRunStatusEnum.EXECUTING;
            // V1 执行完成直接进入汇总，不再经过 REVIEWING
            case EXECUTING -> target == DynamicTeamRunStatusEnum.SUMMARIZING;
            // REVIEWING 保留为后续评审模块的入口，当前 V1 不进入该分支
            case REVIEWING -> target == DynamicTeamRunStatusEnum.REPLANNING
                    || target == DynamicTeamRunStatusEnum.SUMMARIZING;
            case REPLANNING -> target == DynamicTeamRunStatusEnum.VALIDATING;
            default -> false;
        };
    }
}
