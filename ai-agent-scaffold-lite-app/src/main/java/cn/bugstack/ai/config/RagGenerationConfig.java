package cn.bugstack.ai.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 动态团队控制角色与 Worker 共用的固定模型装配。
 *
 * @author xiaoxin
 */
@Configuration
@EnableConfigurationProperties(RagGenerationProperties.class)
public class RagGenerationConfig {

    @Bean("ragChatOpenAiApi")
    public OpenAiApi ragChatOpenAiApi(RagGenerationProperties properties) {
        OpenAiApi.Builder builder = OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .completionsPath(properties.getCompletionsPath());

        // 与 Armory 的 DeepSeek 装配保持一致，在反序列化前缓冲 HTTP 响应体。
        if (properties.getBaseUrl().startsWith("https://api.deepseek.com")) {
            builder.restClientBuilder(RestClient.builder().requestFactory(
                    new BufferingClientHttpRequestFactory(
                            new HttpComponentsClientHttpRequestFactory())));
        }
        return builder.build();
    }

    @Bean("ragChatModel")
    @Primary
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

    @Bean("controlChatModel")
    public ChatModel controlChatModel(
            @Qualifier("ragChatOpenAiApi") OpenAiApi openAiApi,
            RagGenerationProperties properties) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(properties.getModel());
        // DeepSeek 默认先思考；控制角色只需要可解析的最终文本。
        if (properties.getBaseUrl().startsWith("https://api.deepseek.com")) {
            options.reasoningEffort("none");
        }
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options.build())
                .build();
    }

}
