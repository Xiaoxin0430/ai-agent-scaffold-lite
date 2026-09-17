package cn.bugstack.ai.domain.rag.adapter.port;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;

/**
 * 文档文本切分端口。
 *
 * @author xiaoxin
 */
public interface IDocumentChunkerPort {

    /**
     * 将已清洗文档切分为可检索的文本块。
     *
     * @param command 文档切分命令
     * @return 文档切分结果
     */
    ChunkingResultVO chunk(DocumentChunkingCommandEntity command);

}
