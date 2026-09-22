package cn.bugstack.ai.domain.rag.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 已向量化的文档切分块。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentChunkEmbeddingEntity {

    private DocumentChunkEntity chunk;

    private float[] embedding;

    private String embeddingModel;

    private Integer embeddingDimensions;

    private String embeddingVersion;

    /**
     * 避免外部修改实体内部的向量数组。
     */
    public float[] getEmbedding() {
        return embedding == null ? null : embedding.clone();
    }

}
