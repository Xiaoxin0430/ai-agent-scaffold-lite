package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.adapter.port.IPublishedWorkflowConfigPort;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.CanvasValidationResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasConversionResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import cn.bugstack.ai.domain.agent.service.IWorkflowCanvasService;
import cn.bugstack.ai.domain.agent.service.IWorkflowRuntimeRegistry;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowPublishService;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowRuntimeConfigFactory;
import cn.bugstack.ai.types.exception.AppException;
import com.google.adk.runner.InMemoryRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

import java.util.List;

public class WorkflowPublishServiceTest {

    @Test
    public void publishes_converted_config_through_existing_armory() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            AiAgentConfigTableVO config = config("wf_new");
            IWorkflowCanvasService converter = canvas -> new WorkflowCanvasConversionResultVO(
                    new CanvasValidationResultVO(List.of(), List.of("n1")), config);
            IArmoryService armory = tables -> {
                Assert.assertEquals("resolved-key", tables.get(0).getModule().getAiApi().getApiKey());
                context.getBeanFactory().registerSingleton("wf_new", AiAgentRegisterVO.builder()
                        .workflowId("wf_new").workflowName("新工作流").runner(mock(InMemoryRunner.class)).build());
            };
            WorkflowPublishService service = service(converter, armory, context,
                    new MockEnvironment().withProperty("TEST_CANVAS_KEY", "resolved-key"));

            var result = service.publish(new WorkflowCanvasVO());

            Assert.assertTrue(result.published());
            Assert.assertEquals("wf_new", result.workflowId());
            Assert.assertNotNull(context.getBean("wf_new", AiAgentRegisterVO.class));
            Assert.assertEquals("${TEST_CANVAS_KEY:}", config.getModule().getAiApi().getApiKey());
        }
    }

    @Test
    public void missing_secret_does_not_register_workflow() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            IWorkflowCanvasService converter = canvas -> new WorkflowCanvasConversionResultVO(
                    new CanvasValidationResultVO(List.of(), List.of("n1")), config("wf_new"));
            IArmoryService armory = tables -> Assert.fail("Armory must not run without a key");
            WorkflowPublishService service = service(converter, armory, context, new MockEnvironment());

            try {
                service.publish(new WorkflowCanvasVO());
                Assert.fail("Expected missing key rejection");
            } catch (AppException e) {
                Assert.assertEquals("0002", e.getCode());
                Assert.assertFalse(context.containsBean("wf_new"));
            }
        }
    }

    @Test
    public void duplicate_workflow_id_keeps_existing_registration() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            AiAgentRegisterVO old = AiAgentRegisterVO.builder().workflowId("wf_new").build();
            context.getBeanFactory().registerSingleton("wf_new", old);
            IWorkflowCanvasService converter = canvas -> new WorkflowCanvasConversionResultVO(
                    new CanvasValidationResultVO(List.of(), List.of("n1")), config("wf_new"));
            IArmoryService armory = tables -> Assert.fail("Armory must not replace existing workflow");
            WorkflowPublishService service = service(converter, armory, context,
                    new MockEnvironment().withProperty("TEST_CANVAS_KEY", "resolved-key"));

            try {
                service.publish(new WorkflowCanvasVO());
                Assert.fail("Expected duplicate ID rejection");
            } catch (AppException e) {
                Assert.assertEquals("0004", e.getCode());
                Assert.assertSame(old, context.getBean("wf_new"));
            }
        }
    }

    @Test
    public void rejects_placeholder_with_literal_key_fallback() {
        AiAgentConfigTableVO config = config("wf_new");
        config.getModule().getAiApi().setApiKey("${TEST_CANVAS_KEY:literal-secret}");
        WorkflowRuntimeConfigFactory factory = new WorkflowRuntimeConfigFactory();
        ReflectionTestUtils.setField(factory, "environment", new MockEnvironment());
        ReflectionTestUtils.setField(factory, "objectMapper", new ObjectMapper());
        try {
            factory.create(config);
            Assert.fail("Literal key fallback must not be accepted");
        } catch (AppException expected) {
            Assert.assertEquals("0002", expected.getCode());
        }
    }

    private AiAgentConfigTableVO config(String id) {
        AiAgentConfigTableVO config = new AiAgentConfigTableVO();
        AiAgentConfigTableVO.Workflow workflow = new AiAgentConfigTableVO.Workflow();
        workflow.setId(id);
        config.setWorkflow(workflow);
        AiAgentConfigTableVO.Module module = new AiAgentConfigTableVO.Module();
        AiAgentConfigTableVO.Module.AiApi api = new AiAgentConfigTableVO.Module.AiApi();
        api.setApiKey("${TEST_CANVAS_KEY:}");
        module.setAiApi(api);
        config.setModule(module);
        return config;
    }

    private WorkflowPublishService service(IWorkflowCanvasService converter, IArmoryService armory,
                                           GenericApplicationContext context, MockEnvironment environment) {
        WorkflowPublishService service = new WorkflowPublishService();
        ReflectionTestUtils.setField(service, "canvasService", converter);
        ReflectionTestUtils.setField(service, "armoryService", armory);
        ReflectionTestUtils.setField(service, "applicationContext", context);
        WorkflowRuntimeConfigFactory runtimeConfigFactory = new WorkflowRuntimeConfigFactory();
        ReflectionTestUtils.setField(runtimeConfigFactory, "environment", environment);
        ReflectionTestUtils.setField(runtimeConfigFactory, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "runtimeConfigFactory", runtimeConfigFactory);
        ReflectionTestUtils.setField(service, "publishedWorkflowConfigPort", new IPublishedWorkflowConfigPort() {
            @Override public void insert(AiAgentConfigTableVO config) { }
            @Override public List<AiAgentConfigTableVO> findAll() { return List.of(); }
        });
        ReflectionTestUtils.setField(service, "workflowRuntimeRegistry", mock(IWorkflowRuntimeRegistry.class));
        return service;
    }
}
