package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.rag.model.valobj.SimilaritySearchConfigVO;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 向量存储与检索装配。
 *
 * @author xiaoxin
 */
@Configuration
@EnableConfigurationProperties(RagVectorStoreProperties.class)
public class RagVectorStoreConfig {

    @Bean
    public SimilaritySearchConfigVO similaritySearchConfig(
            RagVectorStoreProperties properties) {
        if (properties.getDefaultTopK() <= 0) {
            throw new IllegalArgumentException("rag.vector-store.default-top-k must be positive");
        }
        if (properties.getMaxTopK() < properties.getDefaultTopK()) {
            throw new IllegalArgumentException(
                    "rag.vector-store.max-top-k must not be smaller than default-top-k");
        }
        if (!Double.isFinite(properties.getDefaultMinSimilarity())
                || properties.getDefaultMinSimilarity() < -1.0d
                || properties.getDefaultMinSimilarity() > 1.0d) {
            throw new IllegalArgumentException(
                    "rag.vector-store.default-min-similarity must be in [-1, 1]");
        }

        return SimilaritySearchConfigVO.builder()
                .defaultTopK(properties.getDefaultTopK())
                .maxTopK(properties.getMaxTopK())
                .defaultMinSimilarity(properties.getDefaultMinSimilarity())
                .build();
    }

}
