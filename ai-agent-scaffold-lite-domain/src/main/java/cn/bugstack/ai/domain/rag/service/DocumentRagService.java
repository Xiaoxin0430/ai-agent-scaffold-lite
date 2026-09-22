package cn.bugstack.ai.domain.rag.service;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentCleanerPort;
import cn.bugstack.ai.domain.rag.adapter.port.IDocumentChunkerPort;
import cn.bugstack.ai.domain.rag.adapter.port.IDocumentEmbeddingPort;
import cn.bugstack.ai.domain.rag.adapter.port.IDocumentParserPort;
import cn.bugstack.ai.domain.rag.adapter.port.IDocumentVectorStorePort;
import cn.bugstack.ai.domain.rag.adapter.port.IQueryEmbeddingPort;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkingCommandEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentEmbeddingCommandEntity;
import cn.bugstack.ai.domain.rag.model.entity.SimilaritySearchCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.DocumentIndexResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilaritySearchConfigVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

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
    private IDocumentEmbeddingPort documentEmbeddingPort;

    @Resource
    private IQueryEmbeddingPort queryEmbeddingPort;

    @Resource
    private IDocumentVectorStorePort documentVectorStorePort;

    @Resource
    private ChunkingConfigVO chunkingConfig;

    @Resource
    private EmbeddingConfigVO embeddingConfig;

    @Resource
    private SimilaritySearchConfigVO similaritySearchConfig;

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

    @Override
    public EmbeddingResultVO parseCleanChunkAndEmbed(String documentId,
                                                     String fileName,
                                                     InputStream inputStream) {
        ChunkingResultVO chunkingResult = parseCleanAndChunk(documentId, fileName, inputStream);
        DocumentEmbeddingCommandEntity command = DocumentEmbeddingCommandEntity.builder()
                .chunkingResult(chunkingResult)
                .config(embeddingConfig)
                .build();
        return documentEmbeddingPort.embed(command);
    }

    @Override
    public DocumentIndexResultVO indexDocument(String documentId,
                                               String fileName,
                                               InputStream inputStream) {
        EmbeddingResultVO embeddingResult = parseCleanChunkAndEmbed(
                documentId, fileName, inputStream);
        return documentVectorStorePort.replaceDocument(embeddingResult);
    }

    @Override
    public List<SimilarDocumentChunkVO> searchSimilarChunks(String query,
                                                             Integer topK,
                                                             Double minSimilarity,
                                                             List<String> documentIds) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query cannot be blank");
        }

        int resolvedTopK = topK == null ? similaritySearchConfig.getDefaultTopK() : topK;
        if (resolvedTopK <= 0 || resolvedTopK > similaritySearchConfig.getMaxTopK()) {
            throw new IllegalArgumentException(
                    "topK must be in [1, " + similaritySearchConfig.getMaxTopK() + "]");
        }

        double resolvedMinSimilarity = minSimilarity == null
                ? similaritySearchConfig.getDefaultMinSimilarity()
                : minSimilarity;
        if (!Double.isFinite(resolvedMinSimilarity)
                || resolvedMinSimilarity < -1.0d
                || resolvedMinSimilarity > 1.0d) {
            throw new IllegalArgumentException("minSimilarity must be in [-1, 1]");
        }

        List<String> resolvedDocumentIds = documentIds == null
                ? List.of()
                : documentIds.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList();
        if (documentIds != null && resolvedDocumentIds.size() != documentIds.size()) {
            throw new IllegalArgumentException("documentIds cannot contain blank or duplicated values");
        }

        float[] queryEmbedding = queryEmbeddingPort.embedQuery(query, embeddingConfig);
        SimilaritySearchCommandEntity command = SimilaritySearchCommandEntity.builder()
                .queryEmbedding(queryEmbedding)
                .topK(resolvedTopK)
                .minSimilarity(resolvedMinSimilarity)
                .documentIds(resolvedDocumentIds)
                .build();
        return documentVectorStorePort.search(command);
    }

}
