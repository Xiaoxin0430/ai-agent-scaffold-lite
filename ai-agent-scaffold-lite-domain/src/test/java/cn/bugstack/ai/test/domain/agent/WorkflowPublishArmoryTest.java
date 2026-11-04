package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.adapter.port.IPublishedWorkflowConfigPort;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.CanvasValidationResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasConversionResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import cn.bugstack.ai.domain.agent.service.IWorkflowCanvasService;
import cn.bugstack.ai.domain.agent.service.armory.ArmoryService;
import cn.bugstack.ai.domain.agent.service.armory.SpringWorkflowRuntimeRegistry;
import cn.bugstack.ai.domain.agent.service.armory.AbstractArmorySupport;
import cn.bugstack.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.bugstack.ai.domain.agent.service.armory.node.AgentNode;
import cn.bugstack.ai.domain.agent.service.armory.node.AgentWorkflowNode;
import cn.bugstack.ai.domain.agent.service.armory.node.AiApiNode;
import cn.bugstack.ai.domain.agent.service.armory.node.ChatModelNode;
import cn.bugstack.ai.domain.agent.service.armory.node.RootNode;
import cn.bugstack.ai.domain.agent.service.armory.node.RunnerNode;
import cn.bugstack.ai.domain.agent.service.armory.node.workflow.SequentialAgentNode;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowPublishService;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowRuntimeConfigFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

public class WorkflowPublishArmoryTest {
    @Test
    public void new_workflow_is_built_by_existing_armory_and_registered() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            RootNode root = new RootNode();
            AiApiNode api = new AiApiNode();
            ChatModelNode model = new ChatModelNode();
            AgentNode agent = new AgentNode();
            AgentWorkflowNode workflow = new AgentWorkflowNode();
            SequentialAgentNode sequential = new SequentialAgentNode();
            RunnerNode runner = new RunnerNode();
            SpringWorkflowRuntimeRegistry registry = new SpringWorkflowRuntimeRegistry();
            ReflectionTestUtils.setField(registry, "applicationContext", context);
            ReflectionTestUtils.setField(runner, "workflowRuntimeRegistry", registry);
            for (AbstractArmorySupport node : List.of(root, api, model, agent, workflow, sequential, runner)) {
                ReflectionTestUtils.setField(node, "applicationContext", context);
            }
            ReflectionTestUtils.setField(root, "aiApiNode", api);
            ReflectionTestUtils.setField(api, "chatModelNode", model);
            ReflectionTestUtils.setField(model, "agentNode", agent);
            ReflectionTestUtils.setField(agent, "agentWorkflowNode", workflow);
            ReflectionTestUtils.setField(workflow, "sequentialAgentNode", sequential);
            ReflectionTestUtils.setField(workflow, "runnerNode", runner);
            context.getBeanFactory().registerSingleton("agentWorkflowNode", workflow);
            context.refresh();

            DefaultArmoryFactory factory = new DefaultArmoryFactory();
            ReflectionTestUtils.setField(factory, "rootNode", root);
            ArmoryService armory = new ArmoryService();
            ReflectionTestUtils.setField(armory, "defaultArmoryFactory", factory);
            IWorkflowCanvasService conversion = canvas -> new WorkflowCanvasConversionResultVO(
                    new CanvasValidationResultVO(List.of(), List.of("n1", "n2")), config());
            WorkflowPublishService publish = new WorkflowPublishService();
            ReflectionTestUtils.setField(publish, "canvasService", conversion);
            ReflectionTestUtils.setField(publish, "armoryService", armory);
            ReflectionTestUtils.setField(publish, "applicationContext", context);
            WorkflowRuntimeConfigFactory runtimeConfigFactory = new WorkflowRuntimeConfigFactory();
            ReflectionTestUtils.setField(runtimeConfigFactory, "environment",
                    new MockEnvironment().withProperty("TEST_CANVAS_KEY", "resolved-key"));
            ReflectionTestUtils.setField(runtimeConfigFactory, "objectMapper", new ObjectMapper());
            ReflectionTestUtils.setField(publish, "runtimeConfigFactory", runtimeConfigFactory);
            ReflectionTestUtils.setField(publish, "publishedWorkflowConfigPort", new IPublishedWorkflowConfigPort() {
                @Override public void insert(AiAgentConfigTableVO config) { }
                @Override public List<AiAgentConfigTableVO> findAll() { return List.of(); }
            });
            ReflectionTestUtils.setField(publish, "workflowRuntimeRegistry", registry);

            var result = publish.publish(new WorkflowCanvasVO());

            Assert.assertTrue(result.published());
            AiAgentRegisterVO registered = context.getBean("wf_new", AiAgentRegisterVO.class);
            Assert.assertEquals("wf_new", registered.getWorkflowId());
            Assert.assertNotNull(registered.getRunner());
            registry.unregisterIfSame("wf_new", AiAgentRegisterVO.builder().workflowId("wf_new").build());
            Assert.assertSame(registered, context.getBean("wf_new", AiAgentRegisterVO.class));
            try {
                armory.acceptArmoryAgents(List.of(config()));
                Assert.fail("An existing workflow must not be replaced");
            } catch (cn.bugstack.ai.types.exception.AppException expected) {
                Assert.assertSame(registered, context.getBean("wf_new", AiAgentRegisterVO.class));
            }
        }
    }

    private AiAgentConfigTableVO config() {
        AiAgentConfigTableVO config = new AiAgentConfigTableVO();
        AiAgentConfigTableVO.Workflow workflow = new AiAgentConfigTableVO.Workflow();
        workflow.setId("wf_new");
        workflow.setName("新工作流");
        workflow.setDescription("顺序执行");
        config.setWorkflow(workflow);

        AiAgentConfigTableVO.Module module = new AiAgentConfigTableVO.Module();
        AiAgentConfigTableVO.Module.AiApi api = new AiAgentConfigTableVO.Module.AiApi();
        api.setBaseUrl("https://api.deepseek.com");
        api.setApiKey("${TEST_CANVAS_KEY:}");
        api.setCompletionsPath("/chat/completions");
        api.setEmbeddingsPath("/embeddings");
        module.setAiApi(api);
        AiAgentConfigTableVO.Module.ChatModel model = new AiAgentConfigTableVO.Module.ChatModel();
        model.setModel("deepseek-flash");
        module.setChatModel(model);
        AiAgentConfigTableVO.Module.Agent first = new AiAgentConfigTableVO.Module.Agent();
        first.setName("analyze");
        first.setDescription("分析");
        first.setInstruction("分析需求");
        AiAgentConfigTableVO.Module.Agent second = new AiAgentConfigTableVO.Module.Agent();
        second.setName("write");
        second.setDescription("编写");
        second.setInstruction("根据分析编写方案");
        module.setAgents(List.of(first, second));
        AiAgentConfigTableVO.Module.AgentWorkFlow sequence = new AiAgentConfigTableVO.Module.AgentWorkFlow();
        sequence.setType("sequential");
        sequence.setName("wf_new_seq");
        sequence.setDescription("顺序执行");
        sequence.setSubAgents(List.of("analyze", "write"));
        module.setAgentWorkFlows(List.of(sequence));
        AiAgentConfigTableVO.Module.Runner runner = new AiAgentConfigTableVO.Module.Runner();
        runner.setAgentName("wf_new_seq");
        module.setRunner(runner);
        config.setModule(module);
        return config;
    }
}
