package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamTaskExecutionPort;
import cn.bugstack.ai.domain.agent.service.dynamicteam.execution.DynamicTeamTaskSchedulerService;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamExecutionControlEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamTaskVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerPoolEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;

@Timeout(10)
class DynamicTeamTaskSchedulerServiceTest {

    @Test
    void completionBeforeDeadlineIsNotTimedOutByDelayedCoordinator() {
        DynamicTeamExecutionControlEntity control = new DynamicTeamExecutionControlEntity() {
            @Override
            public synchronized <T> Future<T> submitIfActive(String id, CompletionService<T> service, Callable<T> task) {
                Future<T> future = super.submitIfActive(id, service, task);
                try {
                    future.get(2, TimeUnit.SECONDS);
                    // 故意延迟协调线程超过 1 秒期限；执行线程早已完成。
                    new CountDownLatch(1).await(1100, TimeUnit.MILLISECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                return future;
            }
        };
        List<DynamicTeamTaskExecutionResultVO> results = execute((goal, task, worker, inputs) -> "on_time",
                1, control, task("a", "w1"));
        assertEquals(DynamicTeamTaskStatusEnum.SUCCEEDED, results.get(0).getStatus());
    }

    @Test
    void cancellationAtDispatchBoundaryPreventsSubmission() {
        AtomicInteger calls = new AtomicInteger();
        DynamicTeamExecutionControlEntity control = new DynamicTeamExecutionControlEntity() {
            @Override
            public synchronized <T> Future<T> submitIfActive(String id, CompletionService<T> service, Callable<T> task) {
                cancel();
                return super.submitIfActive(id, service, task);
            }
        };
        List<DynamicTeamTaskExecutionResultVO> results = execute((goal, task, worker, inputs) -> {
            calls.incrementAndGet();
            return "unexpected";
        }, 1, control, task("a", "w1"));
        assertEquals(0, calls.get());
        assertEquals(DynamicTeamTaskStatusEnum.CANCELLED, results.get(0).getStatus());
    }

    @Test
    void cancellationAtAcceptanceBoundaryDiscardsSuccess() {
        DynamicTeamExecutionControlEntity control = new DynamicTeamExecutionControlEntity() {
            @Override
            public synchronized void acceptIfActive(Runnable action) {
                cancel();
                super.acceptIfActive(action);
            }
        };
        List<DynamicTeamTaskExecutionResultVO> results = execute((goal, task, worker, inputs) -> "late",
                1, control, task("a", "w1"), task("b", "w2", "a"));
        assertEquals(DynamicTeamTaskStatusEnum.CANCELLED, results.get(0).getStatus());
        assertEquals(DynamicTeamTaskStatusEnum.CANCELLED, results.get(1).getStatus());
    }

    @Test
    void unlocksChildBeforeUnrelatedSlowBranchCompletes() {
        CountDownLatch childStarted = new CountDownLatch(1);
        IDynamicTeamTaskExecutionPort port = (goal, task, worker, inputs) -> {
            if (task.getTaskId().equals("b")) {
                assertTrue(childStarted.await(3, TimeUnit.SECONDS));
            }
            if (task.getTaskId().equals("c")) {
                assertEquals(Set.of("a"), inputs.keySet());
                assertEquals("result_a", inputs.get("a").getOutput());
                childStarted.countDown();
            }
            return "result_" + task.getTaskId();
        };
        List<DynamicTeamTaskExecutionResultVO> results = execute(port, 2, new DynamicTeamExecutionControlEntity(),
                task("a", "w1"), task("b", "w2"), task("c", "w1", "a"), task("d", "w3", "b", "c"));
        for (DynamicTeamTaskExecutionResultVO result : results) {
            assertEquals(DynamicTeamTaskStatusEnum.SUCCEEDED, result.getStatus());
        }
        assertEquals("d", results.get(3).getTaskId());
    }

    @Test
    void enforcesConcurrencyAndSerializesSameWorkerWithoutBlockingOtherWorkers() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Set<String> busy = ConcurrentHashMap.newKeySet();
        IDynamicTeamTaskExecutionPort port = (goal, task, worker, inputs) -> {
            assertTrue(busy.add(task.getWorkerId()), "同一 Worker 被并发调用");
            int current = active.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            try {
                bothStarted.countDown();
                assertTrue(bothStarted.await(3, TimeUnit.SECONDS));
                return "ok";
            } finally {
                active.decrementAndGet();
                busy.remove(task.getWorkerId());
            }
        };
        List<DynamicTeamTaskExecutionResultVO> results = execute(port, 2, new DynamicTeamExecutionControlEntity(),
                task("a", "w1"), task("b", "w1"), task("c", "w2"), task("d", "w3"));
        assertEquals(2, peak.get());
        assertTrue(results.stream().allMatch(r -> r.getStatus() == DynamicTeamTaskStatusEnum.SUCCEEDED));
    }

    @Test
    void blocksFailedDescendantsButCompletesIndependentBranch() {
        List<DynamicTeamTaskExecutionResultVO> results = execute((goal, task, worker, inputs) -> {
            if (task.getTaskId().equals("a")) throw new IllegalStateException("secret upstream detail");
            return "ok";
        }, 2, new DynamicTeamExecutionControlEntity(), task("a", "w1"), task("b", "w2"),
                task("c", "w3", "a"), task("d", "w1", "c", "b"));
        assertEquals(DynamicTeamTaskStatusEnum.FAILED, results.get(0).getStatus());
        assertEquals(DynamicTeamTaskStatusEnum.SUCCEEDED, results.get(1).getStatus());
        assertEquals(DynamicTeamTaskStatusEnum.BLOCKED, results.get(2).getStatus());
        assertEquals(DynamicTeamTaskStatusEnum.BLOCKED, results.get(3).getStatus());
        assertFalse(results.get(0).getFailureMessage().contains("secret"));
    }

    @Test
    void cancellationStopsDispatchAndIgnoresLateSuccess() {
        DynamicTeamExecutionControlEntity control = new DynamicTeamExecutionControlEntity();
        List<DynamicTeamTaskExecutionResultVO> results = execute((goal, task, worker, inputs) -> {
            control.cancel();
            return "late success";
        }, 1, control, task("a", "w1"), task("b", "w2", "a"));
        assertEquals(DynamicTeamTaskStatusEnum.CANCELLED, results.get(0).getStatus());
        assertEquals(DynamicTeamTaskStatusEnum.CANCELLED, results.get(1).getStatus());
        control.cancel();
        FutureTask<String> future = new FutureTask<>(() -> "must not run");
        control.register("late", future);
        assertTrue(future.isCancelled());
    }

    @Test
    void taskDeadlineCancelsWorkAndDoesNotUnlockChild() {
        List<DynamicTeamTaskExecutionResultVO> results = execute((goal, task, worker, inputs) -> {
            new CountDownLatch(1).await();
            return "unexpected";
        }, 1, new DynamicTeamExecutionControlEntity(), task("a", "w1"), task("b", "w2", "a"));
        assertEquals(DynamicTeamTaskStatusEnum.TIMED_OUT, results.get(0).getStatus());
        assertEquals(DynamicTeamTaskStatusEnum.CANCELLED, results.get(1).getStatus());
    }

    private List<DynamicTeamTaskExecutionResultVO> execute(IDynamicTeamTaskExecutionPort port, int concurrency,
            DynamicTeamExecutionControlEntity control, DynamicTeamTaskVO... tasks) {
        DynamicTeamTaskSchedulerService service = new DynamicTeamTaskSchedulerService();
        ReflectionTestUtils.setField(service, "taskExecutionPort", port);
        DynamicTeamWorkerPoolEntity pool = new DynamicTeamWorkerPoolEntity("run_test", 1);
        Set<String> ids = new LinkedHashSet<>();
        for (DynamicTeamTaskVO task : tasks) ids.add(task.getWorkerId());
        for (String id : ids) {
            DynamicTeamWorkerVO definition = DynamicTeamWorkerVO.builder().workerId(id)
                    .roleName("role").responsibility("responsibility").instruction("business data").build();
            pool.add(new DynamicTeamWorkerRuntimeEntity("run_test", 1, definition,
                    new TestWorkerAgentHandle(id)));
        }
        try {
            return service.execute("goal", DynamicTeamPlanVO.builder().runId("run_test").planVersion(1)
                    .tasks(List.of(tasks)).build(), pool,
                    DynamicTeamRuntimePolicyVO.builder().maxTasks(20).maxConcurrentWorkers(concurrency)
                            .stepTimeoutSeconds(1).build(), control).getTaskResults();
        } finally {
            pool.close();
        }
    }

    private DynamicTeamTaskVO task(String id, String workerId, String... dependencies) {
        return DynamicTeamTaskVO.builder().taskId(id).workerId(workerId).title(id).description("describe")
                .expectedOutput("text").acceptanceCriteria(List.of("complete"))
                .dependsOn(List.of(dependencies)).build();
    }
}
