package cn.bugstack.ai.domain.agent.service.dynamicteam.planning;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 严格解析 Planner 原始输出，不猜测或截取 JSON。
 */
@Component
public class DynamicTeamPlanDraftParser {

    private final ObjectMapper strictObjectMapper;
    //复制objectMapper，打开严格开关
    public DynamicTeamPlanDraftParser(ObjectMapper objectMapper) {
        this.strictObjectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public DynamicTeamPlanDraftVO parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new IllegalArgumentException("Planner 返回内容为空。");
        }

        String json = rawOutput.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Planner 必须只返回一个 JSON 对象。");
        }

        try {
            return strictObjectMapper.readValue(json, DynamicTeamPlanDraftVO.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Planner 返回的计划 JSON 无法严格解析。", exception);
        }
    }

}
