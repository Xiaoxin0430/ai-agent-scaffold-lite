package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 文档向量化装配。
 *
 * @author xiaoxin
 */
@Configuration
@EnableConfigurationProperties(RagEmbeddingProperties.class)
public class RagEmbeddingConfig {

    @Bean
    public EmbeddingConfigVO embeddingConfig(RagEmbeddingProperties properties) {
        return EmbeddingConfigVO.builder()
                .model(properties.getModel())
                .dimensions(properties.getDimensions())
                .batchSize(properties.getBatchSize())
                .embeddingVersion(properties.getVersion())
                .build();
    }

    @Bean("ragOpenAiApi")
    public OpenAiApi ragOpenAiApi(RagEmbeddingProperties properties) {
        return OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .embeddingsPath(properties.getEmbeddingsPath())
                .build();
    }

    @Bean("ragEmbeddingModel")
    public EmbeddingModel ragEmbeddingModel(
            @Qualifier("ragOpenAiApi") OpenAiApi openAiApi,
            RagEmbeddingProperties properties) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.getModel())
                .dimensions(properties.getDimensions())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, options);
    }

}
