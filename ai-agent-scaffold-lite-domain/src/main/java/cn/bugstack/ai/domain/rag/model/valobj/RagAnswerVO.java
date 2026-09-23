package cn.bugstack.ai.domain.rag.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 基于检索上下文生成的答案。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagAnswerVO {

    private String query;

    private String answer;

    /**
     * 实际拼入提示词的检索来源，顺序与提示词中的 S1、S2 一致。
     */
    private List<SimilarDocumentChunkVO> sources;

}
