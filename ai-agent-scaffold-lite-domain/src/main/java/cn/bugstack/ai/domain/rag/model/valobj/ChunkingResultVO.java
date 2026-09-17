package cn.bugstack.ai.domain.rag.model.valobj;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档切分结果。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChunkingResultVO {

    private String documentId;

    private String fileName;

    private String chunkingVersion;

    private Integer chunkCount;

    private List<DocumentChunkEntity> chunks;

}
