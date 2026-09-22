package cn.bugstack.ai.domain.rag.adapter.port;

import cn.bugstack.ai.domain.rag.model.entity.DocumentEmbeddingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;

/**
 * 文档切分块向量化端口。
 *
 * @author xiaoxin
 */
public interface IDocumentEmbeddingPort {

    /**
     * 将已切分的文档块批量转换为语义向量。
     *
     * @param command 向量化命令
     * @return 文档向量化结果
     */
    EmbeddingResultVO embed(DocumentEmbeddingCommandEntity command);

}
