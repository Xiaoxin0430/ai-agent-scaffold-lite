package cn.bugstack.ai.domain.agent.service.canvas;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.CanvasValidationResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Maps only validated V2 canvas data to the existing armory configuration. */
@Service
public class WorkflowCanvasConverter {
    public AiAgentConfigTableVO convert(WorkflowCanvasVO canvas, CanvasValidationResultVO validation) {
        if (canvas == null || validation == null || !validation.valid() || canvas.getNodes() == null
                || validation.orderedNodeIds().isEmpty())
            throw new IllegalArgumentException("画布未通过后端校验。");

        Map<String, WorkflowCanvasVO.AgentNode> byId = new HashMap<>();
        for (WorkflowCanvasVO.AgentNode node : canvas.getNodes()) {
            if (node == null || node.getId() == null || byId.putIfAbsent(node.getId(), node) != null)
                throw new IllegalArgumentException("节点 ID 无效或重复。");
        }
        List<String> order = validation.orderedNodeIds();
        if (order.size() != byId.size() || new HashSet<>(order).size() != order.size()
                || !byId.keySet().containsAll(order))
            throw new IllegalArgumentException("执行顺序未覆盖全部节点。");

        AiAgentConfigTableVO result = new AiAgentConfigTableVO();
        var workflow = new AiAgentConfigTableVO.Workflow();
        workflow.setId(canvas.getWorkflow().getId());
        workflow.setName(canvas.getWorkflow().getName());
        workflow.setDescription(canvas.getWorkflow().getDescription());
        result.setWorkflow(workflow);

        var module = new AiAgentConfigTableVO.Module();
        var aiApi = new AiAgentConfigTableVO.Module.AiApi();
        aiApi.setBaseUrl(canvas.getModule().getAiApi().getBaseUrl());
        aiApi.setApiKey(canvas.getModule().getAiApi().getApiKey());
        aiApi.setCompletionsPath(canvas.getModule().getAiApi().getCompletionsPath());
        aiApi.setEmbeddingsPath(canvas.getModule().getAiApi().getEmbeddingsPath());
        module.setAiApi(aiApi);
        var chatModel = new AiAgentConfigTableVO.Module.ChatModel();
        chatModel.setModel(canvas.getModule().getChatModel().getModel());
        module.setChatModel(chatModel);

        List<AiAgentConfigTableVO.Module.Agent> agents = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String id : order) {
            var source = byId.get(id).getConfig();
            var agent = new AiAgentConfigTableVO.Module.Agent();
            agent.setName(source.getName().trim());
            agent.setDescription(source.getDescription());
            agent.setInstruction(source.getInstruction());
            agent.setOutputKey(source.getOutputKey() == null || source.getOutputKey().isBlank()
                    ? null : source.getOutputKey().trim());
            agents.add(agent);
            names.add(agent.getName());
        }
        module.setAgents(agents);

        String internalName = workflow.getId().replace('-', '_') + "_seq";
        if (names.contains(internalName)) throw new IllegalArgumentException("内部工作流名称与 Agent 名称冲突。");
        var agentWorkFlow = new AiAgentConfigTableVO.Module.AgentWorkFlow();
        agentWorkFlow.setType("sequential");
        agentWorkFlow.setName(internalName);
        agentWorkFlow.setDescription(workflow.getDescription());
        agentWorkFlow.setSubAgents(names);
        module.setAgentWorkFlows(List.of(agentWorkFlow));
        var runner = new AiAgentConfigTableVO.Module.Runner();
        runner.setAgentName(internalName);
        module.setRunner(runner);
        result.setModule(module);
        return result;
    }
}
