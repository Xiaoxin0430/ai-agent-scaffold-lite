package cn.bugstack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 文档切分配置。
 *
 * @author xiaoxin
 */
@Data
@ConfigurationProperties(prefix = "rag.chunking", ignoreInvalidFields = true)
public class RagChunkingProperties {

    private int targetTokens = 600;
    private int maxTokens = 800;
    private int overlapTokens = 100;
    private String version = "v1";
    private String encoding = "CL100K_BASE";

}
