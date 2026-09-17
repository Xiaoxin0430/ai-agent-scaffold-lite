package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * RAG 文档切分装配。
 *
 * @author xiaoxin
 */
@Configuration
@EnableConfigurationProperties(RagChunkingProperties.class)
public class RagChunkingConfig {

    @Bean
    public ChunkingConfigVO chunkingConfig(RagChunkingProperties properties) {
        return ChunkingConfigVO.builder()
                .targetTokens(properties.getTargetTokens())
                .maxTokens(properties.getMaxTokens())
                .overlapTokens(properties.getOverlapTokens())
                .chunkingVersion(properties.getVersion())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(TokenCountEstimator.class)
    public TokenCountEstimator tokenCountEstimator(RagChunkingProperties properties) {
        String encodingName = properties.getEncoding().trim().toUpperCase(Locale.ROOT);
        return new JTokkitTokenCountEstimator(EncodingType.valueOf(encodingName));
    }

}
