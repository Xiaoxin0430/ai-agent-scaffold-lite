package cn.bugstack.ai.domain.agent.service.canvas.validation;

import cn.bugstack.ai.domain.agent.model.valobj.canvas.CanvasValidationIssueVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.CanvasValidationResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent server-side validation of the V1 single sequential chain. */
@Service
public class SequentialWorkflowCanvasValidator {
    //预编译的正则表达式用于后续判断规则
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern WORKFLOW_ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final Pattern KEY_PLACEHOLDER = Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*(?::[^}]*)?}");
    private static final Pattern REFERENCE = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    public CanvasValidationResultVO validate(WorkflowCanvasVO canvas) {
        List<CanvasValidationIssueVO> issues = new ArrayList<>();
        //画布参数校验
        if (canvas == null) {
            issues.add(CanvasValidationIssueVO.canvas("CANVAS_REQUIRED", "画布不能为空。"));
            return invalid(issues);
        }
        if (!"2.0".equals(canvas.getSchemaVersion())) {
            issues.add(CanvasValidationIssueVO.canvas("SCHEMA_VERSION_UNSUPPORTED", "仅支持 2.0 画布协议。"));
        }
        //画布元信息校验
        validateMetadata(canvas, issues);

        List<WorkflowCanvasVO.AgentNode> nodes = canvas.getNodes();
        List<WorkflowCanvasVO.Edge> edges = canvas.getEdges();
        if (nodes == null || nodes.isEmpty()) {
            issues.add(CanvasValidationIssueVO.canvas("NO_AGENTS", "画布至少需要一个 Agent。"));
            return invalid(issues);
        }
        if (edges == null) {
            issues.add(CanvasValidationIssueVO.canvas("EDGES_REQUIRED", "连线列表不能为空。"));
            return invalid(issues);
        }

        Map<String, WorkflowCanvasVO.AgentNode> byId = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<String, String> outputs = new HashMap<>();
        //逐一判断智能体节点
        for (WorkflowCanvasVO.AgentNode node : nodes) {
            if (node == null || blank(node.getId())) {
                issues.add(CanvasValidationIssueVO.canvas("NODE_ID_REQUIRED", "节点 ID 不能为空。"));
                continue;
            }
            String id = node.getId();
            if (byId.putIfAbsent(id, node) != null) {
                issues.add(CanvasValidationIssueVO.node("NODE_ID_DUPLICATE", "节点 ID 重复。", id));
                continue;
            }
            if (!"agent".equals(node.getType())) {
                issues.add(CanvasValidationIssueVO.node("NODE_TYPE_UNSUPPORTED", "仅支持 Agent 节点。", id));
            }
            if (node.getPosition() == null || !finite(node.getPosition().getX())
                    || !finite(node.getPosition().getY())) {
                issues.add(CanvasValidationIssueVO.node("POSITION_INVALID", "节点坐标无效。", id));
            }

            WorkflowCanvasVO.AgentConfig config = node.getConfig();
            if (config == null) {
                issues.add(CanvasValidationIssueVO.node("AGENT_CONFIG_REQUIRED", "节点缺少 Agent 配置。", id));
                continue;
            }
            String name = trim(config.getName());
            if (!IDENTIFIER.matcher(name).matches()) {
                issues.add(CanvasValidationIssueVO.node("AGENT_NAME_INVALID", "Agent 内部名称格式无效。", id));
            } else if (names.putIfAbsent(name, id) != null) {
                issues.add(CanvasValidationIssueVO.node("AGENT_NAME_DUPLICATE", "Agent 内部名称重复：" + name, id));
            }
            if (config.getDescription() == null) {
                issues.add(CanvasValidationIssueVO.node("DESCRIPTION_REQUIRED", "Agent 描述字段缺失。", id));
            }
            if (blank(config.getInstruction())) {
                issues.add(CanvasValidationIssueVO.node("INSTRUCTION_REQUIRED", "Agent 提示词不能为空。", id));
            }

            String key = trim(config.getOutputKey());
            if (config.getOutputKey() != null && !config.getOutputKey().isEmpty() && key.isEmpty()
                    || !key.isEmpty() && !IDENTIFIER.matcher(key).matches()) {
                issues.add(CanvasValidationIssueVO.node("OUTPUT_KEY_INVALID", "输出变量名格式无效。", id));
            } else if (!key.isEmpty() && outputs.putIfAbsent(key, id) != null) {
                issues.add(CanvasValidationIssueVO.node("OUTPUT_KEY_DUPLICATE", "输出变量名重复：" + key, id));
            }
        }

        if (canvas.getWorkflow() != null && !blank(canvas.getWorkflow().getId())) {
            String internalName = canvas.getWorkflow().getId().replace('-', '_') + "_seq";
            String collidingNodeId = names.get(internalName);
            if (collidingNodeId != null)
                issues.add(CanvasValidationIssueVO.node("INTERNAL_NAME_COLLISION",
                        "Agent 名称与内部工作流节点名称冲突。", collidingNodeId));
        }
        //判断连线
        //创建两个空列表，存放出边和入边
        Map<String, List<WorkflowCanvasVO.Edge>> outgoing = new LinkedHashMap<>();
        Map<String, List<WorkflowCanvasVO.Edge>> incoming = new LinkedHashMap<>();
        for (String id : byId.keySet()) {
            outgoing.put(id, new ArrayList<>());
            incoming.put(id, new ArrayList<>());
        }
        Set<String> edgeIds = new HashSet<>();
        for (WorkflowCanvasVO.Edge edge : edges) {
            if (edge == null || blank(edge.getId()) || blank(edge.getSource()) || blank(edge.getTarget())) {
                issues.add(CanvasValidationIssueVO.canvas("EDGE_INVALID", "连线 ID 和端点不能为空。"));
                continue;
            }
            if (!edgeIds.add(edge.getId())) {
                issues.add(CanvasValidationIssueVO.edge("EDGE_ID_DUPLICATE", "连线 ID 重复。", edge.getId()));
            }
            if (!byId.containsKey(edge.getSource()) || !byId.containsKey(edge.getTarget())) {
                issues.add(CanvasValidationIssueVO.edge("EDGE_ENDPOINT_MISSING", "连线指向不存在的 Agent。", edge.getId()));
                continue;
            }
            outgoing.get(edge.getSource()).add(edge);
            incoming.get(edge.getTarget()).add(edge);
        }
        List<String> starts = new ArrayList<>();
        int ends = 0;
        //校验工作流节点
        for (String id : byId.keySet()) {
            if (incoming.get(id).isEmpty()) starts.add(id);
            if (outgoing.get(id).isEmpty()) ends++;
            if (incoming.get(id).size() > 1)
                issues.add(CanvasValidationIssueVO.node("MULTIPLE_PREDECESSORS", "顺序节点不能有多个前驱。", id));
            if (outgoing.get(id).size() > 1)
                issues.add(CanvasValidationIssueVO.node("MULTIPLE_SUCCESSORS", "顺序节点不能有多个后继。", id));
        }
        if (starts.size() != 1)
            issues.add(CanvasValidationIssueVO.canvas("START_COUNT", "顺序工作流必须只有一个起点。"));
        if (ends != 1)
            issues.add(CanvasValidationIssueVO.canvas("END_COUNT", "顺序工作流必须只有一个终点。"));

        //拓扑排序校验环
        Map<String, Integer> degrees = new HashMap<>();
        for (String id : byId.keySet()) {
            degrees.put(id, incoming.get(id).size());
        }
        ArrayDeque<String> queue = new ArrayDeque<>(starts);
        int processed = 0;
        while (!queue.isEmpty()) {
            String id = queue.remove();
            processed++;
            for (WorkflowCanvasVO.Edge edge : outgoing.get(id)) {
                String targetId = edge.getTarget();
                int remainingPredecessors = degrees.get(targetId) - 1;
                degrees.put(targetId, remainingPredecessors);
                if (remainingPredecessors == 0) {
                    queue.add(targetId);
                }
            }
        }
        if (processed != byId.size()) {
            WorkflowCanvasVO.Edge cycleEdge = edges.stream()
                    .filter(edge -> edge != null && degrees.getOrDefault(edge.getSource(), 0) > 0
                            && degrees.getOrDefault(edge.getTarget(), 0) > 0)
                    .findFirst().orElse(null);
            issues.add(cycleEdge == null
                    ? CanvasValidationIssueVO.canvas("CYCLE", "连线中存在环。")
                    : CanvasValidationIssueVO.edge("CYCLE", "这条连线参与了环。", cycleEdge.getId()));
        }

        List<String> order = new ArrayList<>();
        if (starts.size() == 1) {
            Set<String> seen = new HashSet<>();
            String current = starts.get(0);
            while (current != null && seen.add(current)) {
                order.add(current);
                List<WorkflowCanvasVO.Edge> next = outgoing.get(current);
                current = next.size() == 1 ? next.get(0).getTarget() : null;
            }
            for (String id : byId.keySet()) {
                if (!seen.contains(id))
                    issues.add(CanvasValidationIssueVO.node("DISCONNECTED_NODE", "节点不在唯一执行链上。", id));
            }
        }
        if (order.size() == byId.size()) {
            Set<String> produced = new HashSet<>();
            for (String id : order) {
                WorkflowCanvasVO.AgentConfig config = byId.get(id).getConfig();
                if (config == null) continue;
                Matcher matcher = REFERENCE.matcher(config.getInstruction() == null ? "" : config.getInstruction());
                Set<String> seen = new HashSet<>();
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!seen.add(key)) continue;
                    if (!outputs.containsKey(key))
                        issues.add(CanvasValidationIssueVO.node("OUTPUT_REFERENCE_MISSING", "缺少输出变量：" + key, id));
                    else if (!produced.contains(key))
                        issues.add(CanvasValidationIssueVO.node("OUTPUT_REFERENCE_NOT_UPSTREAM", "输出变量不来自上游：" + key, id));
                }
                if (!blank(config.getOutputKey())) produced.add(config.getOutputKey().trim());
            }
        }
        return issues.isEmpty() ? new CanvasValidationResultVO(List.of(), List.copyOf(order)) : invalid(issues);
    }

    //校验画布元信息
    private void validateMetadata(WorkflowCanvasVO canvas, List<CanvasValidationIssueVO> issues) {
        //校验工作流信息
        var workflow = canvas.getWorkflow();
        if (workflow == null || blank(workflow.getId()) || !WORKFLOW_ID.matcher(workflow.getId()).matches()
                || blank(workflow.getName()) || workflow.getDescription() == null) {
            issues.add(CanvasValidationIssueVO.canvas("WORKFLOW_INVALID", "工作流 ID、名称或描述无效。"));
        } else {
            //校验转换为ADK工作流信息后是否合法
            String internal = workflow.getId().replace('-', '_') + "_seq";
            if (!IDENTIFIER.matcher(internal).matches())
                issues.add(CanvasValidationIssueVO.canvas("WORKFLOW_ID_INVALID", "工作流 ID 无法生成内部节点名称。"));
        }
        //校验公共模型信息
        var module = canvas.getModule();
        if (module == null || module.getAiApi() == null || module.getChatModel() == null) {
            issues.add(CanvasValidationIssueVO.canvas("MODULE_REQUIRED", "缺少公共 API 或模型配置。"));
        } else {
            var api = module.getAiApi();
            if (blank(api.getBaseUrl()) || !httpUrl(api.getBaseUrl()) || blank(api.getCompletionsPath())
                    || blank(api.getEmbeddingsPath()) || !api.getCompletionsPath().startsWith("/")
                    || !api.getEmbeddingsPath().startsWith("/"))
                issues.add(CanvasValidationIssueVO.canvas("API_INVALID", "API 地址或路径无效。"));
            if (api.getApiKey() == null || !KEY_PLACEHOLDER.matcher(api.getApiKey()).matches())
                issues.add(CanvasValidationIssueVO.canvas("API_KEY_PLACEHOLDER_REQUIRED", "API 密钥只能使用环境变量占位符。"));
            if (blank(module.getChatModel().getModel()))
                issues.add(CanvasValidationIssueVO.canvas("MODEL_REQUIRED", "公共模型名不能为空。"));
        }
        //校验画布编辑器信息
        var viewport = canvas.getEditor() == null ? null : canvas.getEditor().getViewport();
        if (viewport == null || !finite(viewport.getX()) || !finite(viewport.getY())
                || !finite(viewport.getZoom()) || viewport.getZoom() <= 0)
            issues.add(CanvasValidationIssueVO.canvas("VIEWPORT_INVALID", "画布视口无效。"));
    }

    private static boolean httpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) { return false; }
    }
    private static boolean finite(Double value) { return value != null && Double.isFinite(value); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static CanvasValidationResultVO invalid(List<CanvasValidationIssueVO> issues) {
        return new CanvasValidationResultVO(List.copyOf(issues), List.of());
    }
}
