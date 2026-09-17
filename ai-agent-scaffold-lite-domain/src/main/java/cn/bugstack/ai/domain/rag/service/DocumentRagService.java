package cn.bugstack.ai.domain.rag.service;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentCleanerPort;
import cn.bugstack.ai.domain.rag.adapter.port.IDocumentChunkerPort;
import cn.bugstack.ai.domain.rag.adapter.port.IDocumentParserPort;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * RAG 文档处理领域服务。
 *
 * @author xiaoxin
 */
@Service
public class DocumentRagService implements IDocumentRagService {

    @Resource
    private IDocumentParserPort documentParserPort;

    @Resource
    private IDocumentCleanerPort documentCleanerPort;

    @Resource
    private IDocumentChunkerPort documentChunkerPort;

    @Resource
    private ChunkingConfigVO chunkingConfig;

    @Override
    public CleanedDocumentVO parseAndClean(String fileName, InputStream inputStream) {
        ParsedDocumentVO parsedDocument = documentParserPort.parse(fileName, inputStream);
        return documentCleanerPort.clean(parsedDocument);
    }

    @Override
    public ChunkingResultVO parseCleanAndChunk(String documentId,
                                               String fileName,
                                               InputStream inputStream) {
        CleanedDocumentVO cleanedDocument = parseAndClean(fileName, inputStream);
        DocumentChunkingCommandEntity command = DocumentChunkingCommandEntity.builder()
                .documentId(documentId)
                .cleanedDocument(cleanedDocument)
                .config(chunkingConfig)
                .build();
        return documentChunkerPort.chunk(command);
    }

}
