package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowCanvasConverter;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowCanvasService;
import cn.bugstack.ai.domain.agent.service.canvas.validation.SequentialWorkflowCanvasValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class WorkflowCanvasServiceTest {

    private final WorkflowCanvasService service = new WorkflowCanvasService(
            new SequentialWorkflowCanvasValidator(), new WorkflowCanvasConverter());

    @Test
    public void converts_edges_to_runtime_order_independent_of_array_order() {
        WorkflowCanvasVO canvas = validCanvas();
        canvas.setNodes(List.of(node("n3", "review", "检查 {result2}", null),
                node("n1", "analyze", "分析需求", "result1"),
                node("n2", "write", "根据 {result1} 写方案", "result2")));
        canvas.setEdges(List.of(edge("e2", "n2", "n3"), edge("e1", "n1", "n2")));

        var result = service.validateAndConvert(canvas);

        Assert.assertTrue(result.validation().issues().toString(), result.validation().issues().isEmpty());
        Assert.assertEquals(List.of("n1", "n2", "n3"), result.validation().orderedNodeIds());
        AiAgentConfigTableVO config = result.config();
        Assert.assertEquals("wf_demo", config.getWorkflow().getId());
        Assert.assertEquals("${DeepSeek_API:}", config.getModule().getAiApi().getApiKey());
        Assert.assertEquals("deepseek-flash", config.getModule().getChatModel().getModel());
        Assert.assertEquals(List.of("analyze", "write", "review"),
                config.getModule().getAgentWorkFlows().get(0).getSubAgents());
        Assert.assertEquals("wf_demo_seq", config.getModule().getRunner().getAgentName());
    }

    @Test
    public void rejects_cycle_and_does_not_convert() {
        WorkflowCanvasVO canvas = validCanvas();
        canvas.setEdges(List.of(edge("e1", "n1", "n2"), edge("e2", "n2", "n1")));
        var result = service.validateAndConvert(canvas);
        Assert.assertNull(result.config());
        Assert.assertTrue(result.validation().issues().stream().anyMatch(issue -> issue.code().equals("CYCLE")
                && issue.edgeId() != null));
    }

    @Test
    public void rejects_reference_to_downstream_output() {
        WorkflowCanvasVO canvas = validCanvas();
        canvas.getNodes().get(0).getConfig().setInstruction("使用 {result2}");
        var result = service.validateAndConvert(canvas);
        Assert.assertNull(result.config());
        Assert.assertTrue(result.validation().issues().stream().anyMatch(issue -> issue.code().equals("OUTPUT_REFERENCE_NOT_UPSTREAM")
                && "n1".equals(issue.nodeId())));
    }

    @Test
    public void rejects_plaintext_key_and_duplicate_names() {
        WorkflowCanvasVO canvas = validCanvas();
        canvas.getModule().getAiApi().setApiKey("plain-secret");
        canvas.getNodes().get(1).getConfig().setName("analyze");
        var result = service.validateAndConvert(canvas);
        Assert.assertNull(result.config());
        Assert.assertTrue(result.validation().issues().stream().anyMatch(issue -> issue.code().equals("API_KEY_PLACEHOLDER_REQUIRED")));
        Assert.assertTrue(result.validation().issues().stream().anyMatch(issue -> issue.code().equals("AGENT_NAME_DUPLICATE")
                && "n2".equals(issue.nodeId())));
    }

    @Test
    public void reports_generated_workflow_name_collision_as_validation_issue() {
        WorkflowCanvasVO canvas = validCanvas();
        canvas.getNodes().get(0).getConfig().setName("wf_demo_seq");
        var result = service.validateAndConvert(canvas);
        Assert.assertNull(result.config());
        Assert.assertTrue(result.validation().issues().stream().anyMatch(issue ->
                issue.code().equals("INTERNAL_NAME_COLLISION") && "n1".equals(issue.nodeId())));
    }

    @Test
    public void rejects_branch_and_missing_edge_endpoint() {
        WorkflowCanvasVO canvas = validCanvas();
        canvas.setNodes(List.of(node("n1", "analyze", "分析", null),
                node("n2", "write", "写", null), node("n3", "review", "审", null)));
        canvas.setEdges(List.of(edge("e1", "n1", "n2"), edge("e2", "n1", "n3"),
                edge("e3", "n3", "missing")));
        var result = service.validateAndConvert(canvas);
        Assert.assertNull(result.config());
        Assert.assertTrue(result.validation().issues().stream().anyMatch(issue -> issue.code().equals("MULTIPLE_SUCCESSORS")
                && "n1".equals(issue.nodeId())));
        Assert.assertTrue(result.validation().issues().stream().anyMatch(issue -> issue.code().equals("EDGE_ENDPOINT_MISSING")
                && "e3".equals(issue.edgeId())));
    }

    private WorkflowCanvasVO validCanvas() {
        WorkflowCanvasVO canvas = new WorkflowCanvasVO();
        canvas.setSchemaVersion("2.0");
        var workflow = new WorkflowCanvasVO.Workflow();
        workflow.setId("wf_demo");
        workflow.setName("演示工作流");
        workflow.setDescription("顺序执行");
        canvas.setWorkflow(workflow);
        var module = new WorkflowCanvasVO.Module();
        var api = new WorkflowCanvasVO.AiApi();
        api.setBaseUrl("https://api.deepseek.com");
        api.setApiKey("${DeepSeek_API:}");
        api.setCompletionsPath("/chat/completions");
        api.setEmbeddingsPath("/embeddings");
        module.setAiApi(api);
        var model = new WorkflowCanvasVO.ChatModel();
        model.setModel("deepseek-flash");
        module.setChatModel(model);
        canvas.setModule(module);
        var editor = new WorkflowCanvasVO.Editor();
        var viewport = new WorkflowCanvasVO.Viewport();
        viewport.setX(0.0);
        viewport.setY(0.0);
        viewport.setZoom(1.0);
        editor.setViewport(viewport);
        canvas.setEditor(editor);
        canvas.setNodes(List.of(node("n1", "analyze", "分析需求", "result1"),
                node("n2", "write", "根据 {result1} 写方案", "result2")));
        canvas.setEdges(List.of(edge("e1", "n1", "n2")));
        return canvas;
    }

    private WorkflowCanvasVO.AgentNode node(String id, String name, String instruction, String outputKey) {
        var node = new WorkflowCanvasVO.AgentNode();
        node.setId(id);
        node.setType("agent");
        var position = new WorkflowCanvasVO.Point();
        position.setX(1.0);
        position.setY(2.0);
        node.setPosition(position);
        var config = new WorkflowCanvasVO.AgentConfig();
        config.setName(name);
        config.setDescription("");
        config.setInstruction(instruction);
        config.setOutputKey(outputKey);
        node.setConfig(config);
        return node;
    }

    private WorkflowCanvasVO.Edge edge(String id, String source, String target) {
        var edge = new WorkflowCanvasVO.Edge();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }
}
