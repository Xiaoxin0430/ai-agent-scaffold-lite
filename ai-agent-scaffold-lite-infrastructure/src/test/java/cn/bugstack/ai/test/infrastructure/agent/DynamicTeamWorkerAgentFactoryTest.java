package cn.bugstack.ai.test.infrastructure.agent;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamModelCallBudgetEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamPromptBundleVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamAdkWorkerAgentHandle;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamRagSearchFunctions;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamRagSearchToolFactory;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamWorkerAgentFactory;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicTeamWorkerAgentFactoryTest {

    private DynamicTeamWorkerAgentFactory factory;
    private DynamicTeamRagSearchToolFactory ragSearchToolFactory;
    private BaseTool ragTool;

    @BeforeEach
    void setUp() {
        factory = new DynamicTeamWorkerAgentFactory();
        ragSearchToolFactory = mock(DynamicTeamRagSearchToolFactory.class);
        ragTool = mock(BaseTool.class);
        when(ragTool.name()).thenReturn(DynamicTeamRagSearchFunctions.TOOL_NAME);
        when(ragSearchToolFactory.createTool()).thenReturn(ragTool);

        ReflectionTestUtils.setField(factory, "chatModel", mock(ChatModel.class));
        ReflectionTestUtils.setField(factory, "promptBundle",
                DynamicTeamPromptBundleVO.builder()
                        .workerInstruction("固定 Worker 系统提示")
                        .build());
        ReflectionTestUtils.setField(factory, "ragSearchToolFactory",
                ragSearchToolFactory);
    }

    @Test
    void shouldMountRagToolOnlyWhenCapabilityDeclared() {
        DynamicTeamWorkerVO withRag = worker("analyst",
                List.of(DynamicTeamRagSearchFunctions.TOOL_NAME));
        DynamicTeamWorkerVO withoutRag = worker("writer", List.of());

        List<BaseTool> mounted = factory.resolveTools(withRag);
        List<BaseTool> empty = factory.resolveTools(withoutRag);

        assertEquals(1, mounted.size());
        assertEquals(ragTool, mounted.get(0));
        assertTrue(empty.isEmpty());
        verify(ragSearchToolFactory).createTool();
    }

    @Test
    void shouldNotCreateRagToolWhenCapabilityMissing() {
        factory.resolveTools(worker("writer", null));
        verify(ragSearchToolFactory, never()).createTool();
    }

    @Test
    void createShouldPassResolvedToolsIntoAgent() {
        DynamicTeamWorkerRuntimeEntity runtime = factory.create(
                "run_step4",
                1,
                worker("analyst", List.of(DynamicTeamRagSearchFunctions.TOOL_NAME)),
                new DynamicTeamModelCallBudgetEntity(5));
        try {
            DynamicTeamAdkWorkerAgentHandle handle =
                    (DynamicTeamAdkWorkerAgentHandle) ReflectionTestUtils.getField(
                            runtime, "agentHandle");
            LlmAgent agent = handle.requireAgent();
            assertEquals(1, agent.tools().size());
            assertEquals(ragTool, agent.tools().get(0));
        } finally {
            runtime.close();
        }
    }

    private DynamicTeamWorkerVO worker(String workerId, List<String> capabilities) {
        return DynamicTeamWorkerVO.builder()
                .workerId(workerId)
                .roleName("role")
                .responsibility("responsibility")
                .instruction("business instruction")
                .neededCapabilities(capabilities)
                .build();
    }

}
