package cn.bugstack.ai.api.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 文档索引响应。
 *
 * @author xiaoxin
 */
@Data
public class IndexDocumentResponseDTO {

    private String documentId;
    private String fileName;
    private Integer chunkCount;
    private String embeddingModel;
    private String embeddingVersion;
    private Integer embeddingDimensions;
    private Instant indexedAt;

}
