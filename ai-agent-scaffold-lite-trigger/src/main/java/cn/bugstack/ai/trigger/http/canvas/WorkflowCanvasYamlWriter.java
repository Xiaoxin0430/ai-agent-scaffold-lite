package cn.bugstack.ai.trigger.http.canvas;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** YAML export utility retained for future persistence; publishing uses the configuration object directly. */
@Component
public class WorkflowCanvasYamlWriter {
    public String write(AiAgentConfigTableVO config) {
        if (config == null || config.getWorkflow() == null || config.getModule() == null)
            throw new IllegalArgumentException("运行配置不能为空。");
        var module = config.getModule();
        Map<String, Object> workflow = fields(
                "id", config.getWorkflow().getId(),
                "name", config.getWorkflow().getName(),
                "description", config.getWorkflow().getDescription());
        var api = module.getAiApi();
        Map<String, Object> aiApi = fields(
                "base-url", api.getBaseUrl(), "api-key", api.getApiKey(),
                "completions-path", api.getCompletionsPath(), "embeddings-path", api.getEmbeddingsPath());
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("ai-api", aiApi);
        runtime.put("chat-model", fields("model", module.getChatModel().getModel()));
        List<Map<String, Object>> agents = new ArrayList<>();
        for (var agent : module.getAgents()) {
            var entry = fields("name", agent.getName(), "description", agent.getDescription(),
                    "instruction", agent.getInstruction());
            if (agent.getOutputKey() != null) entry.put("output-key", agent.getOutputKey());
            agents.add(entry);
        }
        runtime.put("agents", agents);
        List<Map<String, Object>> workFlows = new ArrayList<>();
        for (var workFlow : module.getAgentWorkFlows()) {
            workFlows.add(fields("type", workFlow.getType(), "name", workFlow.getName(),
                    "description", workFlow.getDescription(), "sub-agents", workFlow.getSubAgents()));
        }
        runtime.put("agent-work-flows", workFlows);
        runtime.put("runner", fields("agent-name", module.getRunner().getAgentName()));
        Map<String, Object> table = fields("workflow", workflow, "module", runtime);
        Map<String, Object> root = fields("ai", fields("workflow", fields("config",
                fields("tables", fields(config.getWorkflow().getId(), table)))));
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options).dump(root);
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
