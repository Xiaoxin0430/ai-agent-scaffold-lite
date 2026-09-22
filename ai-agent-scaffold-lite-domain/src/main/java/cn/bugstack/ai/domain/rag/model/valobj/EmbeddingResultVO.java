package cn.bugstack.ai.domain.rag.model.valobj;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEmbeddingEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档向量化结果。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmbeddingResultVO {

    private String documentId;

    private String fileName;

    private String embeddingModel;

    private String embeddingVersion;

    private Integer embeddingDimensions;

    private Integer embeddingCount;

    private List<DocumentChunkEmbeddingEntity> embeddings;

}
