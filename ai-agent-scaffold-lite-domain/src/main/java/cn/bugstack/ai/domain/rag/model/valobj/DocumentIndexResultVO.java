package cn.bugstack.ai.domain.rag.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 文档向量入库结果。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentIndexResultVO {

    private String documentId;

    private String fileName;

    private Integer chunkCount;

    private String embeddingModel;

    private String embeddingVersion;

    private Integer embeddingDimensions;

    private Instant indexedAt;

}
