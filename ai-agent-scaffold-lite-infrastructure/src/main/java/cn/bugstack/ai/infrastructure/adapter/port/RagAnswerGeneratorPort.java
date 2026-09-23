package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.rag.adapter.port.IRagAnswerGeneratorPort;
import cn.bugstack.ai.types.exception.RagAnswerGenerationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 基于 Spring AI {@link ChatModel} 的 RAG 答案生成端口实现。
 *
 * @author xiaoxin
 */
@Slf4j
@Service
public class RagAnswerGeneratorPort implements IRagAnswerGeneratorPort {

    private final ChatModel chatModel;

    public RagAnswerGeneratorPort(
            @Qualifier("ragChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String generate(String prompt) {
        try {
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("prompt cannot be blank");
            }
            String answer = chatModel.call(prompt);
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("chat model returned a blank answer");
            }
            log.info("RAG 答案生成完成 promptLength:{} answerLength:{}",
                    prompt.length(), answer.length());
            return answer;
        } catch (Exception e) {
            throw new RagAnswerGenerationException(e);
        }
    }

}
