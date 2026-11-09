package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunContextAggregate;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStartCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunEventTypeEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStatusEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunTerminationReasonEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.IDynamicTeamRunCoordinatorService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.DynamicTeamRunEventHub;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.DynamicTeamRunEventSubscription;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.DynamicTeamRunService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class DynamicTeamRunServiceTest {

    private DynamicTeamRunService service;

    @AfterEach
    void shutdownExecutor() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void startsAsynchronouslyAndReplaysOrderedEventsAfterCursor() throws Exception {
        DynamicTeamRuntimePolicyVO policy = policy();
        DynamicTeamRunEventHub eventHub = new DynamicTeamRunEventHub();
        IDynamicTeamRunCoordinatorService coordinator = new IDynamicTeamRunCoordinatorService() {
            @Override
            public DynamicTeamRunContextAggregate createRun(
                    DynamicTeamRunStartCommandEntity command) {
                return DynamicTeamRunContextAggregate.start(
                        "run_async", command.getUserGoal(), command.getAcceptanceRequirements(),
                        policy, Instant.parse("2026-11-05T00:00:00Z"), 1L, eventHub);
            }

            @Override
            public void executeInitialRound(DynamicTeamRunContextAggregate context) {
                context.transitionTo(DynamicTeamRunStatusEnum.VALIDATING, Instant.now());
                context.terminate(DynamicTeamRunStatusEnum.FAILED,
                        DynamicTeamRunTerminationReasonEnum.PLAN_VALIDATION_FAILED, Instant.now());
                context.markResourcesReleased();
            }

            @Override
            public void cancel(DynamicTeamRunContextAggregate context) {
                context.requestCancellation();
            }
        };
        service = new DynamicTeamRunService();
        ReflectionTestUtils.setField(service, "coordinatorService", coordinator);
        ReflectionTestUtils.setField(service, "runtimePolicy", policy);
        ReflectionTestUtils.setField(service, "eventHub", eventHub);

        service.start(DynamicTeamRunStartCommandEntity.builder()
                .userGoal("测试异步运行")
                .acceptanceRequirements(List.of())
                .build());
        awaitTerminal("run_async");

        try (DynamicTeamRunEventSubscription subscription = service.subscribe("run_async", 1L)) {
            var second = subscription.poll(1, TimeUnit.SECONDS);
            var third = subscription.poll(1, TimeUnit.SECONDS);
            assertNotNull(second);
            assertNotNull(third);
            assertEquals(2L, second.getSequence());
            assertEquals(DynamicTeamRunEventTypeEnum.RUN_STATUS_CHANGED, second.getEventType());
            assertEquals(3L, third.getSequence());
            assertEquals(DynamicTeamRunEventTypeEnum.RUN_TERMINATED, third.getEventType());
            assertNull(subscription.poll(20, TimeUnit.MILLISECONDS));
        }
    }

    private void awaitTerminal(String runId) throws InterruptedException {
        for (int index = 0; index < 100; index++) {
            if (service.query(runId).getStatus().isTerminal()) {
                return;
            }
            Thread.sleep(10L);
        }
        fail("异步运行未在预期时间内结束");
    }

    private DynamicTeamRuntimePolicyVO policy() {
        return DynamicTeamRuntimePolicyVO.builder()
                .enabled(true)
                .maxWorkers(5)
                .maxTasks(20)
                .maxConcurrentWorkers(3)
                .maxReplans(0)
                .stepTimeoutSeconds(2)
                .runTimeoutSeconds(30)
                .maxModelCalls(25)
                .maxUserInputCharacters(20000)
                .build();
    }
}
