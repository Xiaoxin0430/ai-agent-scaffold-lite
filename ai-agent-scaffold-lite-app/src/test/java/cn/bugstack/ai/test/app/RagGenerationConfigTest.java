package cn.bugstack.ai.test.app;

import cn.bugstack.ai.config.RagGenerationConfig;
import cn.bugstack.ai.config.RagGenerationProperties;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * RAG 大模型生成配置测试。
 *
 * @author xiaoxin
 */
public class RagGenerationConfigTest {

    @Test
    public void test_build_generation_configuration() {
        RagGenerationProperties properties = new RagGenerationProperties();
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        RagGenerationConfig config = new RagGenerationConfig();

        OpenAiApi openAiApi = config.ragChatOpenAiApi(properties);
        ChatModel chatModel = config.ragChatModel(openAiApi, properties);

        Assert.assertNotNull(openAiApi);
        Assert.assertNotNull(chatModel);
    }

    @Test
    public void control_model_disables_default_thinking_only_for_deepseek() {
        RagGenerationProperties properties = new RagGenerationProperties();
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setApiKey("test-key");
        properties.setModel("deepseek-flash");
        RagGenerationConfig config = new RagGenerationConfig();
        OpenAiApi api = config.ragChatOpenAiApi(properties);

        OpenAiChatModel control = (OpenAiChatModel) config.controlChatModel(api, properties);
        OpenAiChatModel worker = (OpenAiChatModel) config.ragChatModel(api, properties);

        Assert.assertEquals("none", ((OpenAiChatOptions) control.getDefaultOptions()).getReasoningEffort());
        Assert.assertNull(((OpenAiChatOptions) worker.getDefaultOptions()).getReasoningEffort());
    }

}
