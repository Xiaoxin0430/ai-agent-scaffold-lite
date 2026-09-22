package cn.bugstack.ai.domain.rag.adapter.port;

import cn.bugstack.ai.domain.rag.model.entity.SimilaritySearchCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.DocumentIndexResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;

import java.util.List;

/**
 * 文档向量存储端口。
 *
 * @author xiaoxin
 */
public interface IDocumentVectorStorePort {

    /**
     * 原子替换一个文档的全部 Chunk 及向量。
     *
     * @param embeddingResult 文档向量化结果
     * @return 文档入库结果
     */
    DocumentIndexResultVO replaceDocument(EmbeddingResultVO embeddingResult);

    /**
     * 根据查询向量执行余弦相似度检索。
     *
     * @param command 相似度检索命令
     * @return 按相似度降序排列的 Chunk
     */
    List<SimilarDocumentChunkVO> search(SimilaritySearchCommandEntity command);

}
