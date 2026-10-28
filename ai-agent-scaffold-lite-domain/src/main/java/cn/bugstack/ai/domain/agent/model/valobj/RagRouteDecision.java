package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.Builder;
import lombok.Data;

/** 当前问题是否需要检索知识库。 */
@Data
@Builder
public class RagRouteDecision {
    private boolean retrieve;
    private String searchQuery;
}
