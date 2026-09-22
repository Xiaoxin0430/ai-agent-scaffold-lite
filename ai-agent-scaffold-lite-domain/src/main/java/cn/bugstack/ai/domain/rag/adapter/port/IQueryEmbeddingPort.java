package cn.bugstack.ai.domain.rag.adapter.port;

import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;

/**
 * 检索查询文本向量化端口。
 *
 * @author xiaoxin
 */
public interface IQueryEmbeddingPort {

    /**
     * 将用户查询转换为与文档 Chunk 相同模型、相同维度的向量。
     *
     * @param query 查询文本
     * @param config 向量化配置
     * @return 查询向量
     */
    float[] embedQuery(String query, EmbeddingConfigVO config);

}
