package cn.bugstack.ai.types.exception;

/**
 * RAG 大模型答案生成异常。
 *
 * @author xiaoxin
 */
public class RagAnswerGenerationException extends RuntimeException {

    public RagAnswerGenerationException(Throwable cause) {
        super("RAG 大模型答案生成失败", cause);
    }

}
