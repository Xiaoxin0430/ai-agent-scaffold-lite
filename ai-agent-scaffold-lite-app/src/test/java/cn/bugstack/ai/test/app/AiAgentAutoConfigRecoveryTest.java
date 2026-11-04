package cn.bugstack.ai.test.app;

import cn.bugstack.ai.config.AiAgentAutoConfig;
import cn.bugstack.ai.domain.agent.adapter.port.IPublishedWorkflowConfigPort;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowRuntimeConfigFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AiAgentAutoConfigRecoveryTest {

    @Test
    public void restores_database_workflow_even_without_yaml_workflows() {
        AiAgentConfigTableVO stored = config();
        AtomicInteger assembled = new AtomicInteger();
        AiAgentAutoConfig autoConfig = autoConfig(stored,
                new MockEnvironment().withProperty("TEST_CANVAS_KEY", "resolved-key"),
                tables -> {
                    assembled.incrementAndGet();
                    Assert.assertEquals(1, tables.size());
                    Assert.assertEquals("resolved-key", tables.get(0).getModule().getAiApi().getApiKey());
                });

        autoConfig.onApplicationEvent(null);

        Assert.assertEquals(1, assembled.get());
        Assert.assertEquals("${TEST_CANVAS_KEY:}", stored.getModule().getAiApi().getApiKey());
    }

    @Test
    public void missing_recovery_secret_fails_startup() {
        AiAgentAutoConfig autoConfig = autoConfig(config(), new MockEnvironment(),
                tables -> Assert.fail("Armory must not run without a key"));

        try {
            autoConfig.onApplicationEvent(null);
            Assert.fail("Expected startup failure");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage() != null);
        }
    }

    private AiAgentAutoConfig autoConfig(AiAgentConfigTableVO stored, MockEnvironment environment,
                                         IArmoryService armory) {
        IPublishedWorkflowConfigPort port = new IPublishedWorkflowConfigPort() {
            @Override public void insert(AiAgentConfigTableVO config) { }
            @Override public List<AiAgentConfigTableVO> findAll() { return List.of(stored); }
        };
        AiAgentAutoConfigProperties properties = new AiAgentAutoConfigProperties();
        properties.setTables(Map.of());
        WorkflowRuntimeConfigFactory runtimeFactory = new WorkflowRuntimeConfigFactory();
        ReflectionTestUtils.setField(runtimeFactory, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(runtimeFactory, "environment", environment);
        AiAgentAutoConfig autoConfig = new AiAgentAutoConfig();
        ReflectionTestUtils.setField(autoConfig, "aiAgentAutoConfigProperties", properties);
        ReflectionTestUtils.setField(autoConfig, "armoryService", armory);
        ReflectionTestUtils.setField(autoConfig, "publishedWorkflowConfigPort", port);
        ReflectionTestUtils.setField(autoConfig, "runtimeConfigFactory", runtimeFactory);
        return autoConfig;
    }

    private AiAgentConfigTableVO config() {
        AiAgentConfigTableVO config = new AiAgentConfigTableVO();
        AiAgentConfigTableVO.Workflow workflow = new AiAgentConfigTableVO.Workflow();
        workflow.setId("wf_saved");
        config.setWorkflow(workflow);
        AiAgentConfigTableVO.Module module = new AiAgentConfigTableVO.Module();
        AiAgentConfigTableVO.Module.AiApi api = new AiAgentConfigTableVO.Module.AiApi();
        api.setApiKey("${TEST_CANVAS_KEY:}");
        module.setAiApi(api);
        config.setModule(module);
        return config;
    }
}
