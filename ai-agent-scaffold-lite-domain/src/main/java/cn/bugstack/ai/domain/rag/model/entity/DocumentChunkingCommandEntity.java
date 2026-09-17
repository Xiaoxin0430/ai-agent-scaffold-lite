package cn.bugstack.ai.domain.rag.model.entity;

import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档切分命令实体。
 *
 * @author xiaoxin
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentChunkingCommandEntity {

    /**
     * 由文档管理流程生成的稳定文档标识。
     */
    private String documentId;

    /**
     * 已完成解析和清洗的文档。
     */
    private CleanedDocumentVO cleanedDocument;

    /**
     * 本次切分使用的配置。
     */
    private ChunkingConfigVO config;

}
