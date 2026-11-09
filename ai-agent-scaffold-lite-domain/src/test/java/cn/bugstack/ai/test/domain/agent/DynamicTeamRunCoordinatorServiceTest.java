package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunContextAggregate;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStartCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunEventTypeEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamExecutionStopReasonEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStatusEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunTerminationReasonEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskStatusEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionReportVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.IDynamicTeamPlannerService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.summarization.IDynamicTeamSummarizerService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.execution.IDynamicTeamTaskSchedulerService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.execution.DynamicTeamTaskSchedulerService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanFactory;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidationService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanContextValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanDependencyValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanTaskValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidator;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanWorkerValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.DynamicTeamRunCoordinatorService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.worker.DynamicTeamWorkerLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class DynamicTeamRunCoordinatorServiceTest {

    @Test
    void cancellationDuringPlanningStopsTheControlCallAndReleasesResources() throws Exception {
        CountDownLatch plannerStarted = new CountDownLatch(1);
        CountDownLatch neverComplete = new CountDownLatch(1);
        IDynamicTeamPlannerService planner = command -> {
            plannerStarted.countDown();
            try {
                neverComplete.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("规划调用已中断", exception);
            }
            return null;
        };
        DynamicTeamRuntimePolicyVO policy = runtimePolicy();
        DynamicTeamRunCoordinatorService coordinator = coordinator(
                policy, scheduler(), lifecycle(policy), planner, validationService(),
                command -> "不应汇总");
        DynamicTeamRunContextAggregate context = coordinator.createRun(
                DynamicTeamRunStartCommandEntity.builder()
                        .userGoal("取消规划")
                        .acceptanceRequirements(List.of())
                        .build());

        Thread runThread = new Thread(() -> coordinator.executeInitialRound(context));
        runThread.start();
        assertTrue(plannerStarted.await(2, TimeUnit.SECONDS));
        coordinator.cancel(context);
        runThread.join(2_000);

        assertFalse(runThread.isAlive());
        assertEquals(DynamicTeamRunStatusEnum.CANCELLED, context.getStatus());
        assertEquals(DynamicTeamRunTerminationReasonEnum.USER_CANCELLED,
                context.snapshot().getTerminationReason());
        assertTrue(context.snapshot().isResourcesReleased());
        assertNull(context.snapshot().getCurrentPlan());
    }

    @Test
    void completesRunWithSummarizedAnswerAndReleasesResources() {
        DynamicTeamRuntimePolicyVO policy = runtimePolicy();
        DynamicTeamTaskSchedulerService scheduler = scheduler();
        DynamicTeamWorkerLifecycleService lifecycle = lifecycle(policy);
        DynamicTeamRunCoordinatorService coordinator = coordinator(
                policy, scheduler, lifecycle, planner(), validationService(),
                command -> "最终汇总结果");

        DynamicTeamRunContextAggregate context = coordinator.createRun(
                DynamicTeamRunStartCommandEntity.builder()
                        .userGoal("分析需求并输出实现建议")
                        .acceptanceRequirements(List.of("结论完整", "说明依据"))
                        .build());
        coordinator.executeInitialRound(context);

        assertEquals(DynamicTeamRunStatusEnum.COMPLETED, context.getStatus());
        assertEquals(DynamicTeamRunTerminationReasonEnum.NORMAL_COMPLETION,
                context.snapshot().getTerminationReason());
        assertEquals("最终汇总结果", context.snapshot().getFinalAnswer());
        assertEquals(1, context.snapshot().getCurrentPlan().getPlanVersion());
        assertEquals(2, context.snapshot().getCurrentTaskResults().size());
        assertEquals("worker_result_task_b",
                context.snapshot().getCurrentTaskResults().get(1).getOutput());
        assertTrue(context.snapshot().getEvents().stream()
                .anyMatch(event -> event.getEventType()
                        == DynamicTeamRunEventTypeEnum.TEAM_CREATED));
        assertTrue(context.snapshot().isResourcesReleased());
        assertEquals(DynamicTeamRunEventTypeEnum.RUN_TERMINATED,
                context.snapshot().getEvents()
                        .get(context.snapshot().getEvents().size() - 1)
                        .getEventType());
    }

    @Test
    void rejectsRoundWithFailedTaskWithoutCallingSummarizer() {
        DynamicTeamRuntimePolicyVO policy = runtimePolicy();
        DynamicTeamWorkerLifecycleService lifecycle = lifecycle(policy);
        AtomicInteger summarizeCalls = new AtomicInteger();
        IDynamicTeamSummarizerService summarizer = command -> {
            summarizeCalls.incrementAndGet();
            return "不应生成的汇总";
        };
        IDynamicTeamTaskSchedulerService scheduler = (goal, plan, pool, runtimePolicy, control) ->
                DynamicTeamTaskExecutionReportVO.builder()
                        .stopReason(DynamicTeamExecutionStopReasonEnum.ROUND_COMPLETED)
                        .taskResults(List.of(
                                DynamicTeamTaskExecutionResultVO.builder()
                                        .taskId("task_a")
                                        .workerId("worker_a")
                                        .status(DynamicTeamTaskStatusEnum.FAILED)
                                        .failureMessage("执行失败")
                                        .build(),
                                DynamicTeamTaskExecutionResultVO.builder()
                                        .taskId("task_b")
                                        .workerId("worker_b")
                                        .status(DynamicTeamTaskStatusEnum.SUCCEEDED)
                                        .output("部分结果")
                                        .build()))
                        .build();
        DynamicTeamRunCoordinatorService coordinator = coordinator(
                policy, scheduler, lifecycle, planner(), validationService(), summarizer);

        DynamicTeamRunContextAggregate context = coordinator.createRun(
                DynamicTeamRunStartCommandEntity.builder()
                        .userGoal("分析需求并输出实现建议")
                        .acceptanceRequirements(List.of("结论完整"))
                        .build());
        coordinator.executeInitialRound(context);

        assertEquals(DynamicTeamRunStatusEnum.FAILED, context.getStatus());
        assertEquals(DynamicTeamRunTerminationReasonEnum.TASK_EXECUTION_FAILED,
                context.snapshot().getTerminationReason());
        assertNull(context.snapshot().getFinalAnswer());
        assertEquals(0, summarizeCalls.get());
        assertTrue(context.snapshot().isResourcesReleased());
    }

    @Test
    void cancellationRequestedBeforeSummaryCommitWinsAtomically() {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        DynamicTeamRunContextAggregate context = DynamicTeamRunContextAggregate.start(
                "run_cancel_summary", "生成最终结论", List.of(), runtimePolicy(), now, 1L);
        context.transitionTo(DynamicTeamRunStatusEnum.VALIDATING, now);
        context.transitionTo(DynamicTeamRunStatusEnum.CREATING, now);
        context.transitionTo(DynamicTeamRunStatusEnum.EXECUTING, now);
        context.transitionTo(DynamicTeamRunStatusEnum.SUMMARIZING, now);

        context.requestCancellation();
        boolean completed = context.completeWithFinalAnswer("迟到的汇总结果", now);

        assertFalse(completed);
        assertNull(context.snapshot().getFinalAnswer());
        assertEquals(DynamicTeamRunStatusEnum.SUMMARIZING, context.getStatus());
    }

    private DynamicTeamRunCoordinatorService coordinator(
            DynamicTeamRuntimePolicyVO policy,
            IDynamicTeamTaskSchedulerService scheduler,
            DynamicTeamWorkerLifecycleService lifecycle,
            IDynamicTeamPlannerService planner,
            DynamicTeamPlanValidationService validationService,
            IDynamicTeamSummarizerService summarizer) {
        DynamicTeamRunCoordinatorService coordinator =
                new DynamicTeamRunCoordinatorService();
        ReflectionTestUtils.setField(coordinator, "runtimePolicy", policy);
        ReflectionTestUtils.setField(coordinator, "plannerService", planner);
        ReflectionTestUtils.setField(coordinator, "planValidationService", validationService);
        ReflectionTestUtils.setField(coordinator, "workerLifecycleService", lifecycle);
        ReflectionTestUtils.setField(coordinator, "taskSchedulerService", scheduler);
        ReflectionTestUtils.setField(coordinator, "summarizerService", summarizer);
        return coordinator;
    }

    private DynamicTeamPlanValidationService validationService() {
        DynamicTeamPlanValidator validator = new DynamicTeamPlanValidator(
                new DynamicTeamPlanContextValidationRule(),
                new DynamicTeamPlanWorkerValidationRule(),
                new DynamicTeamPlanTaskValidationRule(),
                new DynamicTeamPlanDependencyValidationRule());
        return new DynamicTeamPlanValidationService(
                validator, new DynamicTeamPlanFactory());
    }

    private DynamicTeamTaskSchedulerService scheduler() {
        DynamicTeamTaskSchedulerService scheduler =
                new DynamicTeamTaskSchedulerService();
        ReflectionTestUtils.setField(scheduler, "taskExecutionPort",
                (cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamTaskExecutionPort)
                        (goal, task, worker, inputs) -> {
                            if (task.getTaskId().equals("task_b")) {
                                assertEquals("worker_result_task_a",
                                        inputs.get("task_a").getOutput());
                            }
                            return "worker_result_" + task.getTaskId();
                        });
        return scheduler;
    }

    private DynamicTeamWorkerLifecycleService lifecycle(
            DynamicTeamRuntimePolicyVO policy) {
        DynamicTeamWorkerLifecycleService lifecycle =
                new DynamicTeamWorkerLifecycleService();
        ReflectionTestUtils.setField(lifecycle, "runtimePolicy", policy);
        ReflectionTestUtils.setField(lifecycle, "workerAgentFactory",
                (cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamWorkerAgentFactory)
                        (runId, planVersion, worker, modelCallBudget) ->
                                new DynamicTeamWorkerRuntimeEntity(
                                runId,
                                planVersion,
                                worker,
                                new TestWorkerAgentHandle(worker.getWorkerId())));
        return lifecycle;
    }

    private IDynamicTeamPlannerService planner() {
        return command -> new DynamicTeamPlanDraftVO(
                "1.0",
                "由两个 Worker 串行完成分析和汇总",
                "根据用户目标生成初始计划",
                List.of(
                        new DynamicTeamPlanDraftVO.WorkerDraft(
                                "worker_a", "分析员", "分析需求", "只输出分析结果"),
                        new DynamicTeamPlanDraftVO.WorkerDraft(
                                "worker_b", "汇总员", "汇总结论", "基于上游结果汇总")),
                List.of(
                        new DynamicTeamPlanDraftVO.TaskDraft(
                                "task_a", "worker_a", "分析", "分析用户目标",
                                "结构化分析", List.of("覆盖主要需求"), List.of()),
                        new DynamicTeamPlanDraftVO.TaskDraft(
                                "task_b", "worker_b", "汇总", "汇总分析结果",
                                "最终建议", List.of("引用上游结果"), List.of("task_a"))));
    }

    private DynamicTeamRuntimePolicyVO runtimePolicy() {
        return DynamicTeamRuntimePolicyVO.builder()
                .enabled(true)
                .maxWorkers(5)
                .maxTasks(20)
                .maxConcurrentWorkers(3)
                .maxReplans(2)
                .stepTimeoutSeconds(2)
                .runTimeoutSeconds(300)
                .maxModelCalls(25)
                .maxUserInputCharacters(20000)
                .build();
    }
}
