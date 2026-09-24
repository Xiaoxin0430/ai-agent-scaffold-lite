package cn.bugstack.ai.domain.rag.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chunk 相似度检索命令。
 *
 * @author xiaoxin
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimilaritySearchCommandEntity {

    private float[] queryEmbedding;

    private Integer topK;

    private Double minSimilarity;

    private List<String> documentIds;

    private String ownerUserId;

    public float[] getQueryEmbedding() {
        return queryEmbedding == null ? null : queryEmbedding.clone();
    }

    public Integer getTopK() {
        return topK;
    }

    public Double getMinSimilarity() {
        return minSimilarity;
    }

    public List<String> getDocumentIds() {
        return documentIds == null ? List.of() : List.copyOf(documentIds);
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

}
