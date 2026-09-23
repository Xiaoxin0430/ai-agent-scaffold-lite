package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.rag.model.valobj.RagAnswerConfigVO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 大模型答案生成装配。
 *
 * @author xiaoxin
 */
@Configuration
@EnableConfigurationProperties(RagGenerationProperties.class)
public class RagGenerationConfig {

    @Bean
    public RagAnswerConfigVO ragAnswerConfig(RagGenerationProperties properties) {
        if (properties.getMaxContextCharacters() <= 0) {
            throw new IllegalArgumentException(
                    "rag.generation.max-context-characters must be positive");
        }
        return RagAnswerConfigVO.builder()
                .maxContextCharacters(properties.getMaxContextCharacters())
                .build();
    }

    @Bean("ragChatOpenAiApi")
    public OpenAiApi ragChatOpenAiApi(RagGenerationProperties properties) {
        return OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .completionsPath(properties.getCompletionsPath())
                .build();
    }

    @Bean("ragChatModel")
    public ChatModel ragChatModel(
            @Qualifier("ragChatOpenAiApi") OpenAiApi openAiApi,
            RagGenerationProperties properties) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getModel())
                        .build())
                .build();
    }

}
