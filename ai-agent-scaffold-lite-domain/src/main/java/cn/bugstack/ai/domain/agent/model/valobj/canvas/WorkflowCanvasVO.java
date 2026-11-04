package cn.bugstack.ai.domain.agent.model.valobj.canvas;

import lombok.Data;

import java.util.List;

/** Editable canvas protocol v2; editor data never reaches the ADK configuration. */
@Data
public class WorkflowCanvasVO {
    private String schemaVersion;
    private Workflow workflow;
    private Module module;
    private Editor editor;
    private List<AgentNode> nodes;
    private List<Edge> edges;

    @Data public static class Workflow {
        private String id;
        private String name;
        private String description;
    }
    @Data public static class Module {
        private AiApi aiApi;
        private ChatModel chatModel;
    }
    @Data public static class AiApi {
        private String baseUrl;
        private String apiKey;
        private String completionsPath;
        private String embeddingsPath;
    }
    @Data public static class ChatModel {
        private String model;
    }
    @Data public static class Editor {
        private Viewport viewport;
    }
    @Data public static class Viewport {
        private Double x;
        private Double y;
        private Double zoom;
    }
    @Data public static class Point {
        private Double x;
        private Double y;
    }
    @Data public static class AgentNode {
        private String id;
        private String type;
        private Point position;
        private AgentConfig config;
    }
    @Data public static class AgentConfig {
        private String name;
        private String description;
        private String instruction;
        private String outputKey;
    }
    @Data public static class Edge {
        private String id;
        private String source;
        private String target;
    }
}
