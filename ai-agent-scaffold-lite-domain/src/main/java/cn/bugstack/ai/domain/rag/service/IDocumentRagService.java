package cn.bugstack.ai.domain.rag.service;

import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.DocumentIndexResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.RagAnswerVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;

import java.io.InputStream;
import java.util.List;

/**
 * RAG 文档处理服务。
 *
 * @author xiaoxin
 */
public interface IDocumentRagService {

    /**
     * 解析并清洗文档。
     *
     * @param fileName    原始文件名
     * @param inputStream 文档输入流
     * @return 文档清洗结果
     */
    CleanedDocumentVO parseAndClean(String fileName, InputStream inputStream);

    /**
     * 解析、清洗并切分文档。
     *
     * @param documentId 稳定文档标识
     * @param fileName 原始文件名
     * @param inputStream 文档输入流
     * @return 文档切分结果
     */
    ChunkingResultVO parseCleanAndChunk(String documentId,
                                        String fileName,
                                        InputStream inputStream);

    /**
     * 解析、清洗、切分并向量化文档。
     *
     * @param documentId 稳定文档标识
     * @param fileName 原始文件名
     * @param inputStream 文档输入流
     * @return 文档向量化结果
     */
    EmbeddingResultVO parseCleanChunkAndEmbed(String documentId,
                                              String fileName,
                                              InputStream inputStream);

    /**
     * 解析、清洗、切分、向量化并原子替换整个文档的索引。
     *
     * @param documentId 稳定文档标识
     * @param fileName 原始文件名
     * @param inputStream 文档输入流
     * @return 文档入库结果
     */
    DocumentIndexResultVO indexDocument(String documentId,
                                        String fileName,
                                        InputStream inputStream);

    DocumentIndexResultVO indexDocument(String documentId, String fileName,
                                        InputStream inputStream, String ownerUserId);

    /**
     * 将查询文本向量化并检索相似 Chunk。
     *
     * @param query 查询文本
     * @param topK 最大返回数，为空时使用默认值
     * @param minSimilarity 最低相似度，为空时使用默认值
     * @param documentIds 可选文档范围
     * @return 按相似度降序排列的 Chunk
     */
    List<SimilarDocumentChunkVO> searchSimilarChunks(String query,
                                                      Integer topK,
                                                      Double minSimilarity,
                                                      List<String> documentIds);

    List<SimilarDocumentChunkVO> searchSimilarChunks(String query, Integer topK,
                                                      Double minSimilarity, List<String> documentIds,
                                                      String ownerUserId);

    /**
     * 检索与问题相关的 Chunk，将检索结果拼入提示词并调用大模型生成答案。
     *
     * @param query 用户问题
     * @param topK 最大检索数，为空时使用默认值
     * @param minSimilarity 最低相似度，为空时使用默认值
     * @param documentIds 可选文档范围
     * @return 答案及实际使用的检索来源
     */
    RagAnswerVO answerQuestion(String query,
                               Integer topK,
                               Double minSimilarity,
                               List<String> documentIds);

}
