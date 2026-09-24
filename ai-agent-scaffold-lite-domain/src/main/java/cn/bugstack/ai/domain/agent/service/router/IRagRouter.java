package cn.bugstack.ai.domain.agent.service.router;

import cn.bugstack.ai.domain.agent.model.valobj.RagRouteDecision;

/** 为当前对话选择直接回答或知识库回答。 */
public interface IRagRouter {

    RagRouteDecision decide(String question, String recentAnswer);

    default RagRouteDecision decide(String question) {
        return decide(question, "");
    }
}
