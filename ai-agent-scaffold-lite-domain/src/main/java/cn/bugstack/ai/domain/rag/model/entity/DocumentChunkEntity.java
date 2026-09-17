package cn.bugstack.ai.domain.rag.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文档切分块实体。
 *
 * @author xiaoxin
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentChunkEntity {

    private String chunkId;

    private String documentId;

    private Integer chunkIndex;

    /**
     * 提供给 Embedding 的文本，包含相邻块重叠内容。
     */
    private String content;

    private Integer tokenCount;

    /**
     * 包含 overlap 后的文本起点。
     */
    private Integer contextStartOffset;

    /**
     * 当前块独立负责的原文起点。
     */
    private Integer coreStartOffset;

    /**
     * 当前块独立负责的原文结束位置，不包含该下标。
     */
    private Integer coreEndOffset;

    private String cleaningVersion;

    private String chunkingVersion;

    private Map<String, Object> metadata;

}
