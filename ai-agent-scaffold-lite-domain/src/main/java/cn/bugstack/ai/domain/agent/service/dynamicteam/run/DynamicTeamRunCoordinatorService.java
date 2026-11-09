package cn.bugstack.ai.domain.agent.service.dynamicteam.run;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamRunEventPublisher;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunContextAggregate;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamExecutionControlEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStartCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamSummarizingCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerPoolEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamExecutionStopReasonEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningModeEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStatusEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunTerminationReasonEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskStatusEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationResultVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionReportVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.IDynamicTeamPlanValidationService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.IDynamicTeamPlannerService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.summarization.IDynamicTeamSummarizerService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.execution.IDynamicTeamTaskSchedulerService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.worker.IDynamicTeamWorkerLifecycleService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.exception.DynamicTeamModelCallLimitException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStatusEnum.*;

/**
 * 运行级协调服务：串联规划、校验、建队和单轮执行，不复制各模块内部算法。
 */
@Service
@Slf4j
public class DynamicTeamRunCoordinatorService
        implements IDynamicTeamRunCoordinatorService {

    @Resource
    private IDynamicTeamPlannerService plannerService;
    @Resource
    private IDynamicTeamPlanValidationService planValidationService;
    @Resource
    private IDynamicTeamWorkerLifecycleService workerLifecycleService;
    @Resource
    private IDynamicTeamTaskSchedulerService taskSchedulerService;
    @Resource
    private IDynamicTeamSummarizerService summarizerService;
    @Resource
    private DynamicTeamRuntimePolicyVO runtimePolicy;
    @Resource
    private IDynamicTeamRunEventPublisher eventPublisher = IDynamicTeamRunEventPublisher.NO_OP;

    private Clock clock = Clock.systemUTC();
    private LongSupplier nanoTimeSupplier = System::nanoTime;
    private Supplier<String> runIdSupplier = () -> UUID.randomUUID().toString();

    //创建运行上下文聚会对象
    @Override
    public DynamicTeamRunContextAggregate createRun(DynamicTeamRunStartCommandEntity command) {
        return DynamicTeamRunContextAggregate.start(
                runIdSupplier.get(),
                command.getUserGoal(),
                command.getAcceptanceRequirements(),
                runtimePolicy,
                now(),
                nanoTimeSupplier.getAsLong(),
                eventPublisher);
    }

    //进入协调流程
    @Override
    public void executeInitialRound(DynamicTeamRunContextAggregate contextAggregate) {
        if (contextAggregate == null || contextAggregate.getStatus() != PLANNING) {
            throw new IllegalArgumentException("运行上下文必须处于 PLANNING 状态。");
        }

        try {
            ensureCanContinue(contextAggregate);

            // 规划阶段只调用 Planner 一次；无效输出也消耗已经预占的模型额度。
            DynamicTeamPlanDraftVO draft = executeInitialPlanning(contextAggregate);
            ensureCanContinue(contextAggregate);

            // 计划必须通过后端校验并分配正式版本，模型草稿不能直接进入运行时。
            contextAggregate.transitionTo(VALIDATING, now());
            DynamicTeamPlanValidationResultVO validationResult =
                    planValidationService.validateAndBuild(
                            buildValidationCommand(contextAggregate, draft));
            if (!validationResult.isValid()) {
                contextAggregate.terminate(FAILED,
                        DynamicTeamRunTerminationReasonEnum.PLAN_VALIDATION_FAILED,
                        now());
                return;
            }
            ensureCanContinue(contextAggregate);

            DynamicTeamPlanVO acceptedPlan = validationResult.getAcceptedPlan();
            contextAggregate.recordPlan(acceptedPlan, now());

            // WorkerPool 只在正式计划通过后创建，并由运行上下文持有到终态清理。
            contextAggregate.transitionTo(CREATING, now());
            DynamicTeamWorkerPoolEntity pool =
                    workerLifecycleService.createTeam(
                            acceptedPlan, contextAggregate.getModelCallBudget());
            contextAggregate.attachWorkerPool(pool, now());
            ensureCanContinue(contextAggregate);

            // 模块 6 负责一轮 DAG 调度，模块 7 只接收进度、结果和停止原因。
            contextAggregate.transitionTo(EXECUTING, now());
            DynamicTeamExecutionControlEntity control =
                    new DynamicTeamExecutionControlEntity(
                            contextAggregate.getDeadlineNanos(),
                            progress -> contextAggregate.recordTaskProgress(progress, now()));
            contextAggregate.attachExecutionControl(control);
            DynamicTeamTaskExecutionReportVO executionReport;
            try {
                executionReport = taskSchedulerService.execute(
                        contextAggregate.getUserGoal(),
                        acceptedPlan,
                        pool,
                        runtimePolicy,
                        control);
            } finally {
                contextAggregate.clearExecutionControl(control);
            }
            contextAggregate.recordTaskResults(
                    acceptedPlan.getPlanVersion(),
                    executionReport.getTaskResults());

            // V1 全部任务成功后进入汇总，不经过评审；异常终态由 terminateByExecutionReason 处理。
            if (executionReport.getStopReason()
                    == DynamicTeamExecutionStopReasonEnum.ROUND_COMPLETED) {
                if (allPlanTasksSucceeded(
                        acceptedPlan, executionReport.getTaskResults())) {
                    contextAggregate.transitionTo(SUMMARIZING, now());
                    executeSummarizing(
                            contextAggregate, acceptedPlan, executionReport.getTaskResults());
                } else {
                    contextAggregate.terminate(FAILED,
                            DynamicTeamRunTerminationReasonEnum.TASK_EXECUTION_FAILED,
                            now());
                }
            } else {
                terminateByExecutionReason(contextAggregate,
                        executionReport.getStopReason());
            }
        } catch (DynamicTeamModelCallLimitException exception) {
            contextAggregate.terminate(FAILED,
                    DynamicTeamRunTerminationReasonEnum.MODEL_CALL_LIMIT_REACHED,
                    now());
        } catch (DynamicTeamRunStageException exception) {
            terminateControlStage(contextAggregate,
                    exception.targetStatus, exception.terminationReason);
        } catch (RuntimeException exception) {
            terminateFailedRun(contextAggregate);
        } finally {
            cleanupIfTerminal(contextAggregate);
        }
    }

    @Override
    public void cancel(DynamicTeamRunContextAggregate context) {
        if (context == null) {
            return;
        }

        // 执行中只发出取消请求，由协调线程保存部分结果后再发布终态。
        // SUMMARIZING 和 REVIEWING 是同步阻塞的控制调用，直接发 cancel 信号并置为终态。
        context.requestCancellation();
        if (context.getStatus() == SUMMARIZING || context.getStatus() == REVIEWING) {
            context.terminate(CANCELLED,
                    DynamicTeamRunTerminationReasonEnum.USER_CANCELLED,
                    now());
            cleanupIfTerminal(context);
        }
    }

    private DynamicTeamPlanDraftVO executeInitialPlanning(
            DynamicTeamRunContextAggregate contextAggregate) {
        long remainingNanos = contextAggregate.remainingRunNanos(
                nanoTimeSupplier.getAsLong());
        if (remainingNanos <= 0L) {
            throw new DynamicTeamRunStageException(
                    TIMEOUT, DynamicTeamRunTerminationReasonEnum.RUN_TIMEOUT);
        }
        contextAggregate.reserveModelCall();
        DynamicTeamPlanningCommandEntity planningCommand =
                DynamicTeamPlanningCommandEntity.builder()
                        .runId(contextAggregate.getRunId())
                        .mode(DynamicTeamPlanningModeEnum.INITIAL)
                        .userGoal(contextAggregate.getUserGoal())
                        .acceptanceRequirements(contextAggregate.getAcceptanceRequirements())
                        .runtimePolicy(runtimePolicy)
                        .remainingModelCalls(
                                contextAggregate.getModelCallBudget().getRemainingModelCalls())
                        .remainingRunSeconds(
                                contextAggregate.remainingRunSeconds(
                                        nanoTimeSupplier.getAsLong()))
                        .completedTasks(List.of())
                        .reviewIssues(List.of())
                        .build();

        long timeoutNanos = Math.min(
                TimeUnit.SECONDS.toNanos(
                        runtimePolicy.getStepTimeoutSeconds()),
                remainingNanos);
        return executeControlCall(contextAggregate, timeoutNanos,
                "dynamic-team-planner",
                DynamicTeamRunTerminationReasonEnum.PLANNING_FAILED,
                () -> plannerService.plan(planningCommand));
    }

    /**
     * 汇总阶段：从执行报告中筛选 SUCCEEDED 任务，调用 Summarizer 生成最终回答。
     * 结构与 executeInitialPlanning 对称：预占预算 → daemon 线程限时等待 → 写入上下文。
     * 汇总失败不能把空文本包装为成功，直接进入 FAILED 终态。
     */
    private void executeSummarizing(
            DynamicTeamRunContextAggregate context,
            DynamicTeamPlanVO acceptedPlan,
            List<DynamicTeamTaskExecutionResultVO> allResults) {

        // 再次检查取消和超时，避免执行完成后立即被取消再进入无意义汇总。
        ensureCanContinue(context);

        long remainingNanos = context.remainingRunNanos(nanoTimeSupplier.getAsLong());
        if (remainingNanos <= 0L) {
            throw new DynamicTeamRunStageException(
                    TIMEOUT, DynamicTeamRunTerminationReasonEnum.RUN_TIMEOUT);
        }

        // 只把状态为 SUCCEEDED 的任务结果传给 Summarizer；失败/取消/阻断任务不传入。
        List<DynamicTeamTaskExecutionResultVO> succeededResults = allResults.stream()
                .filter(r -> r.getStatus() == DynamicTeamTaskStatusEnum.SUCCEEDED)
                .toList();

        context.reserveModelCall();

        DynamicTeamSummarizingCommandEntity summaryCommand =
                DynamicTeamSummarizingCommandEntity.builder()
                        .runId(context.getRunId())
                        .userGoal(context.getUserGoal())
                        .acceptanceRequirements(context.getAcceptanceRequirements())
                        .succeededResults(succeededResults)
                        .remainingModelCalls(context.getModelCallBudget().getRemainingModelCalls())
                        .remainingRunSeconds(
                                context.remainingRunSeconds(nanoTimeSupplier.getAsLong()))
                        .build();

        long timeoutNanos = Math.min(
                TimeUnit.SECONDS.toNanos(runtimePolicy.getStepTimeoutSeconds()),
                remainingNanos);
        String answer = executeControlCall(context, timeoutNanos,
                "dynamic-team-summarizer",
                DynamicTeamRunTerminationReasonEnum.SUMMARIZING_FAILED,
                () -> summarizerService.summarize(summaryCommand));
        if (!context.completeWithFinalAnswer(answer, now())) {
            throw new DynamicTeamRunStageException(
                    CANCELLED, DynamicTeamRunTerminationReasonEnum.USER_CANCELLED);
        }
    }

    /** 控制角色共用限时等待与取消句柄；业务输入、预算和输出提交仍由各阶段负责。 */
    private <T> T executeControlCall(
            DynamicTeamRunContextAggregate context,
            long timeoutNanos,
            String threadName,
            DynamicTeamRunTerminationReasonEnum failureReason,
            Callable<T> call) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        Future<T> future = executor.submit(call);
        context.attachControlCallFuture(future);
        try {
            return future.get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            DynamicTeamRunTerminationReasonEnum reason =
                    context.isRunTimedOut(nanoTimeSupplier.getAsLong())
                            ? DynamicTeamRunTerminationReasonEnum.RUN_TIMEOUT
                            : DynamicTeamRunTerminationReasonEnum.MODEL_CALL_TIMEOUT;
            throw new DynamicTeamRunStageException(TIMEOUT, reason);
        } catch (CancellationException exception) {
            throw new DynamicTeamRunStageException(
                    CANCELLED, DynamicTeamRunTerminationReasonEnum.USER_CANCELLED);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new DynamicTeamRunStageException(
                    CANCELLED, DynamicTeamRunTerminationReasonEnum.USER_CANCELLED);
        } catch (ExecutionException exception) {
            throw new DynamicTeamRunStageException(FAILED, failureReason);
        } finally {
            context.clearControlCallFuture(future);
            executor.shutdownNow();
        }
    }

    private DynamicTeamPlanValidationCommandEntity buildValidationCommand(
            DynamicTeamRunContextAggregate context,
            DynamicTeamPlanDraftVO draft) {
        return DynamicTeamPlanValidationCommandEntity.builder()
                .runId(context.getRunId())
                .planningMode(DynamicTeamPlanningModeEnum.INITIAL)
                .planDraft(draft)
                .runtimePolicy(runtimePolicy)
                .build();
    }

    /**
     * ROUND_COMPLETED 只表示调度自然结束；这里再次核对正式计划中的每个任务都唯一成功，
     * 避免把失败、阻断、缺失或重复结果的执行报告包装成完整汇总。
     */
    private boolean allPlanTasksSucceeded(
            DynamicTeamPlanVO plan,
            List<DynamicTeamTaskExecutionResultVO> results) {
        if (plan == null || results == null
                || results.size() != plan.getTasks().size()) {
            return false;
        }

        Set<String> expectedTaskIds = new HashSet<>();
        for (var task : plan.getTasks()) {
            expectedTaskIds.add(task.getTaskId());
        }

        Set<String> actualTaskIds = new HashSet<>();
        for (DynamicTeamTaskExecutionResultVO result : results) {
            if (result == null
                    || result.getStatus() != DynamicTeamTaskStatusEnum.SUCCEEDED
                    || result.getTaskId() == null
                    || !expectedTaskIds.contains(result.getTaskId())
                    || !actualTaskIds.add(result.getTaskId())) {
                return false;
            }
        }
        return actualTaskIds.equals(expectedTaskIds);
    }

    private void terminateByExecutionReason(
            DynamicTeamRunContextAggregate context,
            DynamicTeamExecutionStopReasonEnum stopReason) {
        if (stopReason == DynamicTeamExecutionStopReasonEnum.USER_CANCELLED) {
            context.terminate(CANCELLED,
                    DynamicTeamRunTerminationReasonEnum.USER_CANCELLED, now());
            return;
        }
        if (stopReason == DynamicTeamExecutionStopReasonEnum.MODEL_CALL_TIMEOUT) {
            context.terminate(TIMEOUT,
                    DynamicTeamRunTerminationReasonEnum.MODEL_CALL_TIMEOUT, now());
            return;
        }
        if (stopReason == DynamicTeamExecutionStopReasonEnum.RUN_TIMEOUT) {
            context.terminate(TIMEOUT,
                    DynamicTeamRunTerminationReasonEnum.RUN_TIMEOUT, now());
            return;
        }
        if (stopReason == DynamicTeamExecutionStopReasonEnum.MODEL_CALL_LIMIT_REACHED) {
            context.terminate(FAILED,
                    DynamicTeamRunTerminationReasonEnum.MODEL_CALL_LIMIT_REACHED,
                    now());
            return;
        }
        context.terminate(FAILED,
                DynamicTeamRunTerminationReasonEnum.TASK_EXECUTION_FAILED,
                now());
    }

    private void terminateFailedRun(
            DynamicTeamRunContextAggregate context) {
        if (context.getStatus().isTerminal()) {
            return;
        }
        DynamicTeamRunTerminationReasonEnum reason = switch (context.getStatus()) {
            case PLANNING -> DynamicTeamRunTerminationReasonEnum.PLANNING_FAILED;
            case VALIDATING -> DynamicTeamRunTerminationReasonEnum.PLAN_VALIDATION_FAILED;
            case CREATING -> DynamicTeamRunTerminationReasonEnum.TEAM_CREATION_FAILED;
            case EXECUTING -> DynamicTeamRunTerminationReasonEnum.TASK_EXECUTION_FAILED;
            case SUMMARIZING -> DynamicTeamRunTerminationReasonEnum.SUMMARIZING_FAILED;
            default -> DynamicTeamRunTerminationReasonEnum.INTERNAL_ERROR;
        };
        context.terminate(FAILED, reason, now());
    }

    private void terminateControlStage(
            DynamicTeamRunContextAggregate context,
            DynamicTeamRunStatusEnum targetStatus,
            DynamicTeamRunTerminationReasonEnum terminationReason) {
        if (targetStatus == TIMEOUT) {
            context.terminate(TIMEOUT, terminationReason, now());
            return;
        }
        if (targetStatus == CANCELLED) {
            context.terminate(CANCELLED,
                    DynamicTeamRunTerminationReasonEnum.USER_CANCELLED, now());
            return;
        }
        // FAILED：直接使用抛出方给定的 terminationReason，避免把汇总失败误报为规划失败。
        context.terminate(FAILED, terminationReason, now());
    }

    private void cleanupIfTerminal(
            DynamicTeamRunContextAggregate context) {
        if (!context.getStatus().isTerminal()
                || context.snapshot().isResourcesReleased()) {
            return;
        }

        // 清理异常不能恢复运行或覆盖已经确定的业务终态。
        context.requestCancellation();
        if (!context.hasWorkerPool()) {
            context.markResourcesReleased();
            return;
        }
        try {
            workerLifecycleService.cleanup(context.getRequiredWorkerPool());
            context.markResourcesReleased();
        } catch (RuntimeException exception) {
            log.warn("动态团队终态资源清理失败 runId={}, errorType={}",
                    context.getRunId(), exception.getClass().getSimpleName());
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }

    //校验是否有取消请求和超过运行总时限
    private void ensureCanContinue(DynamicTeamRunContextAggregate contextAggregate) {
        //是否有取消请求
        if (contextAggregate.isCancellationRequested()) {
            throw new DynamicTeamRunStageException(
                    CANCELLED,
                    DynamicTeamRunTerminationReasonEnum.USER_CANCELLED);
        }
        //是否超过运行总时限
        if (contextAggregate.isRunTimedOut(nanoTimeSupplier.getAsLong())) {
            throw new DynamicTeamRunStageException(
                    TIMEOUT,
                    DynamicTeamRunTerminationReasonEnum.RUN_TIMEOUT);
        }
    }

    /** 只在协调服务内部携带同步控制调用的停止类型，不作为公共领域异常暴露。 */
    private static class DynamicTeamRunStageException
            extends RuntimeException {
        private final DynamicTeamRunStatusEnum targetStatus;
        private final DynamicTeamRunTerminationReasonEnum terminationReason;

        private DynamicTeamRunStageException(
                DynamicTeamRunStatusEnum targetStatus,
                DynamicTeamRunTerminationReasonEnum terminationReason) {
            this.targetStatus = targetStatus;
            this.terminationReason = terminationReason;
        }
    }
}
