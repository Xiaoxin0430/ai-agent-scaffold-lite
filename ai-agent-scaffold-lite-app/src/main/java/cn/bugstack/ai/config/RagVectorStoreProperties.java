package cn.bugstack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 向量存储与检索配置。
 *
 * @author xiaoxin
 */
@Data
@ConfigurationProperties(prefix = "rag.vector-store", ignoreInvalidFields = true)
public class RagVectorStoreProperties {

    private int defaultTopK = 5;
    private int maxTopK = 50;
    private double defaultMinSimilarity = 0.0d;

}
