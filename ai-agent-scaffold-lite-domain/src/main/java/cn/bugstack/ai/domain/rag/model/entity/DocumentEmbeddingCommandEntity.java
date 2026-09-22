package cn.bugstack.ai.domain.rag.model.entity;

import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档向量化命令。
 *
 * @author xiaoxin
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentEmbeddingCommandEntity {

    private ChunkingResultVO chunkingResult;

    private EmbeddingConfigVO config;

}
