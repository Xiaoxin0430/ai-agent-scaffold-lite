package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.infrastructure.adapter.port.RagAnswerGeneratorPort;
import cn.bugstack.ai.types.exception.RagAnswerGenerationException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Spring AI RAG 答案生成适配器测试。
 *
 * @author xiaoxin
 */
public class RagAnswerGeneratorPortTest {

    @Test
    public void test_generate_delegates_complete_prompt_to_chat_model() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        Mockito.when(chatModel.call("prompt with retrieved context"))
                .thenReturn("grounded answer");
        RagAnswerGeneratorPort port = new RagAnswerGeneratorPort(chatModel);

        String answer = port.generate("prompt with retrieved context");

        Assert.assertEquals("grounded answer", answer);
        Mockito.verify(chatModel).call("prompt with retrieved context");
    }

    @Test(expected = RagAnswerGenerationException.class)
    public void test_generate_rejects_blank_answer() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        Mockito.when(chatModel.call("prompt")).thenReturn(" ");
        new RagAnswerGeneratorPort(chatModel).generate("prompt");
    }

}
