package cn.bugstack.ai.domain.rag.model.valobj;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 相似文档 Chunk。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimilarDocumentChunkVO {

    private DocumentChunkEntity chunk;

    private String fileName;

    private String embeddingModel;

    private String embeddingVersion;

    private Integer embeddingDimensions;

    private Double similarity;

}
