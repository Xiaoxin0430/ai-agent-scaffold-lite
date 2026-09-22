package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentVectorStorePort;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEmbeddingEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.entity.SimilaritySearchCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.DocumentIndexResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.types.exception.DocumentVectorStoreException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 基于 PostgreSQL + pgvector 的文档向量存储实现。
 *
 * @author xiaoxin
 */
@Slf4j
@Repository
public class PostgresDocumentVectorStorePort implements IDocumentVectorStorePort {

    private static final int DATABASE_VECTOR_DIMENSIONS = 1024;
    private static final int INSERT_BATCH_SIZE = 100;

    private static final String UPSERT_DOCUMENT_SQL = """
            INSERT INTO rag_document (
                document_id, file_name, chunk_count, cleaning_version,
                chunking_version, embedding_model, embedding_version,
                embedding_dimensions, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (document_id) DO UPDATE SET
                file_name = EXCLUDED.file_name,
                chunk_count = EXCLUDED.chunk_count,
                cleaning_version = EXCLUDED.cleaning_version,
                chunking_version = EXCLUDED.chunking_version,
                embedding_model = EXCLUDED.embedding_model,
                embedding_version = EXCLUDED.embedding_version,
                embedding_dimensions = EXCLUDED.embedding_dimensions,
                updated_at = CURRENT_TIMESTAMP
            """;

    private static final String INSERT_CHUNK_SQL = """
            INSERT INTO rag_document_chunk (
                chunk_id, document_id, chunk_index, content, token_count,
                context_start_offset, core_start_offset, core_end_offset,
                cleaning_version, chunking_version, metadata, embedding,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector,
                      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresDocumentVectorStorePort(JdbcTemplate jdbcTemplate,
                                           ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentIndexResultVO replaceDocument(EmbeddingResultVO embeddingResult) {
        String documentId = resolveDocumentId(embeddingResult);
        String operationName = "input-validation";
        try {
            validateEmbeddingResult(embeddingResult);
            List<DocumentChunkEmbeddingEntity> embeddings = embeddingResult.getEmbeddings();
            DocumentChunkEntity firstChunk = embeddings.get(0).getChunk();

            operationName = "document-lock";
            jdbcTemplate.query(
                    "SELECT pg_advisory_xact_lock(hashtext(?))",
                    resultSet -> {
                    },
                    documentId);

            operationName = "document-upsert";
            jdbcTemplate.update(
                    UPSERT_DOCUMENT_SQL,
                    embeddingResult.getDocumentId(),
                    embeddingResult.getFileName(),
                    embeddingResult.getEmbeddingCount(),
                    firstChunk.getCleaningVersion(),
                    firstChunk.getChunkingVersion(),
                    embeddingResult.getEmbeddingModel(),
                    embeddingResult.getEmbeddingVersion(),
                    embeddingResult.getEmbeddingDimensions());

            operationName = "old-chunk-delete";
            jdbcTemplate.update(
                    "DELETE FROM rag_document_chunk WHERE document_id = ?",
                    documentId);

            operationName = "chunk-batch-insert";
            jdbcTemplate.batchUpdate(
                    INSERT_CHUNK_SQL,
                    embeddings,
                    INSERT_BATCH_SIZE,
                    (statement, item) -> bindChunk(statement, item));

            Instant indexedAt = Instant.now();
            log.info(
                    "文档向量入库完成 documentId:{} fileName:{} chunkCount:{} model:{} dimensions:{} version:{}",
                    documentId,
                    embeddingResult.getFileName(),
                    embeddingResult.getEmbeddingCount(),
                    embeddingResult.getEmbeddingModel(),
                    embeddingResult.getEmbeddingDimensions(),
                    embeddingResult.getEmbeddingVersion());

            return DocumentIndexResultVO.builder()
                    .documentId(documentId)
                    .fileName(embeddingResult.getFileName())
                    .chunkCount(embeddingResult.getEmbeddingCount())
                    .embeddingModel(embeddingResult.getEmbeddingModel())
                    .embeddingVersion(embeddingResult.getEmbeddingVersion())
                    .embeddingDimensions(embeddingResult.getEmbeddingDimensions())
                    .indexedAt(indexedAt)
                    .build();
        } catch (Exception e) {
            throw new DocumentVectorStoreException(documentId, operationName, e);
        }
    }

    @Override
    public List<SimilarDocumentChunkVO> search(SimilaritySearchCommandEntity command) {
        String operationName = "search-input-validation";
        try {
            validateSearchCommand(command);
            String vectorLiteral = toVectorLiteral(command.getQueryEmbedding());
            List<String> documentIds = command.getDocumentIds();

            StringBuilder sql = new StringBuilder("""
                    SELECT c.chunk_id, c.document_id, c.chunk_index, c.content,
                           c.token_count, c.context_start_offset, c.core_start_offset,
                           c.core_end_offset, c.cleaning_version, c.chunking_version,
                           c.metadata, d.file_name, d.embedding_model,
                           d.embedding_version, d.embedding_dimensions,
                           1 - (c.embedding <=> ?::vector) AS similarity
                    FROM rag_document_chunk c
                    INNER JOIN rag_document d ON d.document_id = c.document_id
                    WHERE 1 - (c.embedding <=> ?::vector) >= ?
                    """);

            List<Object> parameters = new ArrayList<>();
            parameters.add(vectorLiteral);
            parameters.add(vectorLiteral);
            parameters.add(command.getMinSimilarity());

            if (!documentIds.isEmpty()) {
                StringJoiner placeholders = new StringJoiner(", ");
                for (String documentId : documentIds) {
                    placeholders.add("?");
                    parameters.add(documentId);
                }
                sql.append(" AND c.document_id IN (")
                        .append(placeholders)
                        .append(")\n");
            }

            sql.append(" ORDER BY c.embedding <=> ?::vector, c.document_id, c.chunk_index LIMIT ?");
            parameters.add(vectorLiteral);
            parameters.add(command.getTopK());

            operationName = "similarity-query";
            List<SimilarDocumentChunkVO> results = jdbcTemplate.query(
                    sql.toString(),
                    this::mapSearchResult,
                    parameters.toArray());

            log.info(
                    "Chunk 相似度检索完成 topK:{} minSimilarity:{} documentFilterCount:{} resultCount:{}",
                    command.getTopK(),
                    command.getMinSimilarity(),
                    documentIds.size(),
                    results.size());
            return List.copyOf(results);
        } catch (Exception e) {
            throw new DocumentVectorStoreException("search", operationName, e);
        }
    }

    private void bindChunk(java.sql.PreparedStatement statement,
                           DocumentChunkEmbeddingEntity item) throws SQLException {
        DocumentChunkEntity chunk = item.getChunk();
        statement.setString(1, chunk.getChunkId());
        statement.setString(2, chunk.getDocumentId());
        statement.setInt(3, chunk.getChunkIndex());
        statement.setString(4, chunk.getContent());
        statement.setInt(5, chunk.getTokenCount());
        statement.setInt(6, chunk.getContextStartOffset());
        statement.setInt(7, chunk.getCoreStartOffset());
        statement.setInt(8, chunk.getCoreEndOffset());
        statement.setString(9, chunk.getCleaningVersion());
        statement.setString(10, chunk.getChunkingVersion());
        statement.setString(11, serializeMetadata(chunk.getMetadata()));
        statement.setString(12, toVectorLiteral(item.getEmbedding()));
    }

    private SimilarDocumentChunkVO mapSearchResult(ResultSet resultSet,
                                                    int rowNumber) throws SQLException {
        DocumentChunkEntity chunk = DocumentChunkEntity.builder()
                .chunkId(resultSet.getString("chunk_id"))
                .documentId(resultSet.getString("document_id"))
                .chunkIndex(resultSet.getInt("chunk_index"))
                .content(resultSet.getString("content"))
                .tokenCount(resultSet.getInt("token_count"))
                .contextStartOffset(resultSet.getInt("context_start_offset"))
                .coreStartOffset(resultSet.getInt("core_start_offset"))
                .coreEndOffset(resultSet.getInt("core_end_offset"))
                .cleaningVersion(resultSet.getString("cleaning_version"))
                .chunkingVersion(resultSet.getString("chunking_version"))
                .metadata(deserializeMetadata(resultSet.getString("metadata")))
                .build();

        return SimilarDocumentChunkVO.builder()
                .chunk(chunk)
                .fileName(resultSet.getString("file_name"))
                .embeddingModel(resultSet.getString("embedding_model"))
                .embeddingVersion(resultSet.getString("embedding_version"))
                .embeddingDimensions(resultSet.getInt("embedding_dimensions"))
                .similarity(resultSet.getDouble("similarity"))
                .build();
    }

    private void validateEmbeddingResult(EmbeddingResultVO result) {
        Objects.requireNonNull(result, "embeddingResult cannot be null");
        if (isBlank(result.getDocumentId())) {
            throw new IllegalArgumentException("documentId cannot be blank");
        }
        if (isBlank(result.getFileName())) {
            throw new IllegalArgumentException("fileName cannot be blank");
        }
        if (isBlank(result.getEmbeddingModel())) {
            throw new IllegalArgumentException("embeddingModel cannot be blank");
        }
        if (isBlank(result.getEmbeddingVersion())) {
            throw new IllegalArgumentException("embeddingVersion cannot be blank");
        }
        if (!Objects.equals(result.getEmbeddingDimensions(), DATABASE_VECTOR_DIMENSIONS)) {
            throw new IllegalArgumentException(
                    "embeddingDimensions must be " + DATABASE_VECTOR_DIMENSIONS);
        }
        if (result.getEmbeddings() == null || result.getEmbeddings().isEmpty()) {
            throw new IllegalArgumentException("embeddings cannot be empty");
        }
        if (!Objects.equals(result.getEmbeddingCount(), result.getEmbeddings().size())) {
            throw new IllegalArgumentException("embeddingCount does not match embeddings size");
        }

        DocumentChunkEmbeddingEntity firstEmbedding = Objects.requireNonNull(
                result.getEmbeddings().get(0), "first embedding cannot be null");
        DocumentChunkEntity firstChunk = Objects.requireNonNull(
                firstEmbedding.getChunk(), "first chunk cannot be null");
        String cleaningVersion = firstChunk.getCleaningVersion();
        String chunkingVersion = firstChunk.getChunkingVersion();
        if (isBlank(cleaningVersion) || isBlank(chunkingVersion)) {
            throw new IllegalArgumentException("chunk versions cannot be blank");
        }

        HashSet<String> chunkIds = new HashSet<>();
        for (int index = 0; index < result.getEmbeddings().size(); index++) {
            DocumentChunkEmbeddingEntity item = Objects.requireNonNull(
                    result.getEmbeddings().get(index), "embedding cannot be null, index=" + index);
            DocumentChunkEntity chunk = Objects.requireNonNull(
                    item.getChunk(), "chunk cannot be null, index=" + index);
            if (!Objects.equals(result.getDocumentId(), chunk.getDocumentId())) {
                throw new IllegalArgumentException("chunk documentId does not match, index=" + index);
            }
            if (!Objects.equals(chunk.getChunkIndex(), index)) {
                throw new IllegalArgumentException("chunk index is not continuous, index=" + index);
            }
            if (isBlank(chunk.getChunkId()) || !chunkIds.add(chunk.getChunkId())) {
                throw new IllegalArgumentException("chunkId is blank or duplicated, index=" + index);
            }
            if (isBlank(chunk.getContent())
                    || chunk.getTokenCount() == null
                    || chunk.getTokenCount() <= 0) {
                throw new IllegalArgumentException("invalid chunk content or token count, index=" + index);
            }
            if (chunk.getContextStartOffset() == null
                    || chunk.getCoreStartOffset() == null
                    || chunk.getCoreEndOffset() == null
                    || chunk.getContextStartOffset() < 0
                    || chunk.getCoreStartOffset() < chunk.getContextStartOffset()
                    || chunk.getCoreEndOffset() <= chunk.getCoreStartOffset()) {
                throw new IllegalArgumentException("invalid chunk offsets, index=" + index);
            }
            if (!Objects.equals(cleaningVersion, chunk.getCleaningVersion())
                    || !Objects.equals(chunkingVersion, chunk.getChunkingVersion())) {
                throw new IllegalArgumentException("chunk versions do not match, index=" + index);
            }
            if (!Objects.equals(result.getEmbeddingModel(), item.getEmbeddingModel())
                    || !Objects.equals(result.getEmbeddingVersion(), item.getEmbeddingVersion())
                    || !Objects.equals(result.getEmbeddingDimensions(), item.getEmbeddingDimensions())) {
                throw new IllegalArgumentException("embedding metadata does not match, index=" + index);
            }
            float[] vector = item.getEmbedding();
            if (vector == null || vector.length != DATABASE_VECTOR_DIMENSIONS) {
                throw new IllegalArgumentException("invalid vector dimensions, index=" + index);
            }
            for (float value : vector) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "embedding contains a non-finite value, index=" + index);
                }
            }
        }
    }

    private void validateSearchCommand(SimilaritySearchCommandEntity command) {
        Objects.requireNonNull(command, "search command cannot be null");
        float[] vector = command.getQueryEmbedding();
        if (vector == null || vector.length != DATABASE_VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "query embedding dimensions must be " + DATABASE_VECTOR_DIMENSIONS);
        }
        if (command.getTopK() == null || command.getTopK() <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (command.getMinSimilarity() == null
                || !Double.isFinite(command.getMinSimilarity())
                || command.getMinSimilarity() < -1.0d
                || command.getMinSimilarity() > 1.0d) {
            throw new IllegalArgumentException("minSimilarity must be in [-1, 1]");
        }
        for (String documentId : command.getDocumentIds()) {
            if (isBlank(documentId)) {
                throw new IllegalArgumentException("documentId filter cannot be blank");
            }
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("chunk metadata serialization failed", e);
        }
    }

    private Map<String, Object> deserializeMetadata(String metadata) throws SQLException {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> result = objectMapper.readValue(
                    metadata, new TypeReference<Map<String, Object>>() {
                    });
            return result == null ? Map.of() : Map.copyOf(result);
        } catch (JsonProcessingException e) {
            throw new SQLException("chunk metadata deserialization failed", e);
        }
    }

    private String toVectorLiteral(float[] vector) {
        StringJoiner values = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding contains a non-finite value");
            }
            values.add(Float.toString(value));
        }
        return values.toString();
    }

    private String resolveDocumentId(EmbeddingResultVO result) {
        return result == null || isBlank(result.getDocumentId())
                ? "unknown"
                : result.getDocumentId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
