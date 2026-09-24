package cn.bugstack.ai.domain.agent.model.valobj;

/** 当前问题是否需要检索知识库。 */
public record RagRouteDecision(boolean retrieve, String searchQuery) {

    public static RagRouteDecision direct() {
        return new RagRouteDecision(false, "");
    }

    public static RagRouteDecision retrieve(String query) {
        return new RagRouteDecision(true, query);
    }
}
