package cn.bugstack.ai.domain.rag.service;

import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;

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

}
