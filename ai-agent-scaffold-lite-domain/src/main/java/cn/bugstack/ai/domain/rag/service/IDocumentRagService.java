package cn.bugstack.ai.domain.rag.service;

import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;

import java.io.InputStream;

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

}
