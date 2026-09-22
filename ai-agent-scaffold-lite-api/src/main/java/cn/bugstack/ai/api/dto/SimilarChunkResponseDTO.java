package cn.bugstack.ai.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 相似 Chunk 响应。
 *
 * @author xiaoxin
 */
@Data
public class SimilarChunkResponseDTO {

    private String chunkId;
    private String documentId;
    private String fileName;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private Integer contextStartOffset;
    private Integer coreStartOffset;
    private Integer coreEndOffset;
    private String cleaningVersion;
    private String chunkingVersion;
    private String embeddingModel;
    private String embeddingVersion;
    private Integer embeddingDimensions;
    private Double similarity;
    private Map<String, Object> metadata;

}
