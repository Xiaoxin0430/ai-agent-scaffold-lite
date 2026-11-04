package cn.bugstack.ai.test.trigger;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.trigger.http.canvas.WorkflowCanvasYamlWriter;
import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

public class WorkflowCanvasYamlWriterTest {
    @Test
    public void writes_same_runtime_order_and_secret_placeholder() {
        var config = new AiAgentConfigTableVO();
        var workflow = new AiAgentConfigTableVO.Workflow();
        workflow.setId("wf_demo"); workflow.setName("需求处理"); workflow.setDescription("演示");
        config.setWorkflow(workflow);
        var module = new AiAgentConfigTableVO.Module();
        var api = new AiAgentConfigTableVO.Module.AiApi();
        api.setBaseUrl("https://api.deepseek.com"); api.setApiKey("${DeepSeek_API:}");
        api.setCompletionsPath("/chat/completions"); api.setEmbeddingsPath("/embeddings");
        module.setAiApi(api);
        var model = new AiAgentConfigTableVO.Module.ChatModel(); model.setModel("deepseek-flash");
        module.setChatModel(model);
        var agent = new AiAgentConfigTableVO.Module.Agent();
        agent.setName("analyze"); agent.setDescription("分析"); agent.setInstruction("先分析\n再总结");
        module.setAgents(List.of(agent));
        var workFlow = new AiAgentConfigTableVO.Module.AgentWorkFlow();
        workFlow.setType("sequential"); workFlow.setName("wf_demo_seq");
        workFlow.setDescription("演示"); workFlow.setSubAgents(List.of("analyze"));
        module.setAgentWorkFlows(List.of(workFlow));
        var runner = new AiAgentConfigTableVO.Module.Runner(); runner.setAgentName("wf_demo_seq");
        module.setRunner(runner); config.setModule(module);

        String yaml = new WorkflowCanvasYamlWriter().write(config);
        Map<?, ?> root = new Yaml().load(yaml);
        Map<?, ?> table = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) root.get("ai"))
                .get("workflow")).get("config")).get("tables");
        Map<?, ?> value = (Map<?, ?>) table.get("wf_demo");
        Map<?, ?> outputModule = (Map<?, ?>) value.get("module");
        Assert.assertEquals("${DeepSeek_API:}", ((Map<?, ?>) outputModule.get("ai-api")).get("api-key"));
        Assert.assertEquals("deepseek-flash", ((Map<?, ?>) outputModule.get("chat-model")).get("model"));
        Assert.assertEquals(List.of("analyze"), ((Map<?, ?>) ((List<?>) outputModule.get("agent-work-flows")).get(0)).get("sub-agents"));
        Assert.assertEquals("先分析\n再总结", ((Map<?, ?>) ((List<?>) outputModule.get("agents")).get(0)).get("instruction"));
        Assert.assertEquals("wf_demo_seq", ((Map<?, ?>) outputModule.get("runner")).get("agent-name"));
    }
}
