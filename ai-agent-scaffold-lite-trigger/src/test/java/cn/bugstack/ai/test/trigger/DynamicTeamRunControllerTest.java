package cn.bugstack.ai.test.trigger;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStatusEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunSnapshotVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.IDynamicTeamRunService;
import cn.bugstack.ai.trigger.http.DynamicTeamRunController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class DynamicTeamRunControllerTest {

    private DynamicTeamRunController controller;

    @After
    public void shutdown() {
        if (controller != null) {
            controller.shutdownStreamExecutor();
        }
    }

    @Test
    public void capabilitiesAndSnapshotUseStableEnvelope() throws Exception {
        IDynamicTeamRunService service = mock(IDynamicTeamRunService.class);
        when(service.capabilities()).thenReturn(policy());
        when(service.query("run_1")).thenReturn(DynamicTeamRunSnapshotVO.builder()
                .runId("run_1")
                .userGoal("形成交付")
                .acceptanceRequirements(List.of("结论完整"))
                .status(DynamicTeamRunStatusEnum.PLANNING)
                .planHistory(List.of())
                .currentTaskResults(List.of())
                .usedModelCalls(1)
                .remainingModelCalls(24)
                .startedAt(Instant.parse("2026-11-05T00:00:00Z"))
                .events(List.of())
                .build());
        controller = new DynamicTeamRunController();
        ReflectionTestUtils.setField(controller, "dynamicTeamRunService", service);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        String capabilities = mvc.perform(get("/api/v1/dynamic-team/capabilities"))
                .andReturn().getResponse().getContentAsString();
        String snapshot = mvc.perform(get("/api/v1/dynamic-team/runs/run_1"))
                .andReturn().getResponse().getContentAsString();

        Assert.assertTrue(capabilities.contains("\"code\":\"0000\""));
        Assert.assertTrue(capabilities.contains("\"maxWorkers\":5"));
        Assert.assertTrue(snapshot.contains("\"runId\":\"run_1\""));
        Assert.assertTrue(snapshot.contains("\"status\":\"PLANNING\""));
        Assert.assertTrue(snapshot.contains("\"lastEventSequence\":0"));
    }

    private DynamicTeamRuntimePolicyVO policy() {
        return DynamicTeamRuntimePolicyVO.builder()
                .enabled(true)
                .maxWorkers(5)
                .maxTasks(20)
                .maxConcurrentWorkers(3)
                .maxReplans(0)
                .stepTimeoutSeconds(60)
                .runTimeoutSeconds(300)
                .maxModelCalls(25)
                .maxUserInputCharacters(20000)
                .build();
    }
}
