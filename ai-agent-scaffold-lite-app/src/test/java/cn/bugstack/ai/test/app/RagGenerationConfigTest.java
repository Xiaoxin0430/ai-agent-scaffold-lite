package cn.bugstack.ai.test.app;

import cn.bugstack.ai.config.RagGenerationConfig;
import cn.bugstack.ai.config.RagGenerationProperties;
import cn.bugstack.ai.domain.rag.model.valobj.RagAnswerConfigVO;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.model.ChatModel;
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
        properties.setMaxContextCharacters(8000);
        RagGenerationConfig config = new RagGenerationConfig();

        RagAnswerConfigVO answerConfig = config.ragAnswerConfig(properties);
        OpenAiApi openAiApi = config.ragChatOpenAiApi(properties);
        ChatModel chatModel = config.ragChatModel(openAiApi, properties);

        Assert.assertEquals(Integer.valueOf(8000),
                answerConfig.getMaxContextCharacters());
        Assert.assertNotNull(openAiApi);
        Assert.assertNotNull(chatModel);
    }

}
