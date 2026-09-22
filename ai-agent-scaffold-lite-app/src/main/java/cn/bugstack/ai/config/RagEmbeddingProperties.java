package cn.bugstack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 文档向量化配置。
 *
 * @author xiaoxin
 */
@Data
@ConfigurationProperties(prefix = "rag.embedding", ignoreInvalidFields = true)
public class RagEmbeddingProperties {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey = "";
    private String embeddingsPath = "/embeddings";
    private String model = "qwen3.7-text-embedding-flash";
    private int dimensions = 1024;
    private int batchSize = 20;
    private String version = "v1";

}
