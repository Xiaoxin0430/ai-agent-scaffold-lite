package cn.bugstack.ai.domain.rag.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RAG 答案生成配置。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagAnswerConfigVO {

    /**
     * 单次提示词允许拼入的检索正文最大字符数。
     */
    private Integer maxContextCharacters;

}
