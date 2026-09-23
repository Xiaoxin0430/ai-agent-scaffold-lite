package cn.bugstack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 大模型答案生成配置。
 *
 * @author xiaoxin
 */
@Data
@ConfigurationProperties(prefix = "rag.generation", ignoreInvalidFields = true)
public class RagGenerationProperties {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey = "";
    private String completionsPath = "/chat/completions";
    private String model = "qwen3.8-27b";
    private int maxContextCharacters = 12000;

}
