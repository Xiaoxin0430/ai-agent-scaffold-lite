package cn.bugstack.ai.domain.rag.adapter.port;

/**
 * RAG 答案生成端口。
 *
 * @author xiaoxin
 */
public interface IRagAnswerGeneratorPort {

    /**
     * 调用大模型，根据完整提示词生成答案。
     *
     * @param prompt 已包含检索上下文和用户问题的提示词
     * @return 大模型生成的答案
     */
    String generate(String prompt);

}
