package cn.bugstack.ai.test.integration;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEmbeddingEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.entity.SimilaritySearchCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.infrastructure.adapter.repository.PostgresDocumentVectorStorePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresDocumentVectorStoreIT {

    private static final int VECTOR_DIMENSIONS = 1024;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));

    private static JdbcTemplate jdbcTemplate;
    private static PostgresDocumentVectorStorePort vectorStore;

    @BeforeAll
    static void initializeDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        vectorStore = new PostgresDocumentVectorStorePort(jdbcTemplate, new ObjectMapper());
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE rag_document CASCADE");
    }

    @Test
    void shouldRunFlywayAndPersistVectorDocument() {
        vectorStore.replaceDocument(embeddingResult("doc-a", "chunk-a", unitVector(0)), "user-a");

        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_document WHERE document_id = 'doc-a'", Integer.class);
        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_document_chunk WHERE document_id = 'doc-a'", Integer.class);

        assertThat(documentCount).isEqualTo(1);
        assertThat(chunkCount).isEqualTo(1);
    }

    @Test
    void shouldIsolateSimilaritySearchByOwner() {
        vectorStore.replaceDocument(embeddingResult("doc-a", "chunk-a", unitVector(0)), "user-a");
        vectorStore.replaceDocument(embeddingResult("doc-b", "chunk-b", unitVector(0)), "user-b");

        List<?> ownerAResults = vectorStore.search(searchCommand("user-a", unitVector(0)));
        List<?> unknownOwnerResults = vectorStore.search(searchCommand("user-c", unitVector(0)));

        assertThat(ownerAResults).hasSize(1);
        assertThat(unknownOwnerResults).isEmpty();
    }

    private static SimilaritySearchCommandEntity searchCommand(String ownerUserId, float[] query) {
        return SimilaritySearchCommandEntity.builder()
                .queryEmbedding(query)
                .topK(10)
                .minSimilarity(0.0d)
                .documentIds(List.of())
                .ownerUserId(ownerUserId)
                .build();
    }

    private static EmbeddingResultVO embeddingResult(String documentId, String chunkId, float[] vector) {
        DocumentChunkEntity chunk = DocumentChunkEntity.builder()
                .chunkId(chunkId)
                .documentId(documentId)
                .chunkIndex(0)
                .content("住宿费用上限为 500 元。")
                .tokenCount(10)
                .contextStartOffset(0)
                .coreStartOffset(0)
                .coreEndOffset(14)
                .cleaningVersion("v1")
                .chunkingVersion("v1")
                .metadata(Map.of("source", "integration-test"))
                .build();
        DocumentChunkEmbeddingEntity embedding = DocumentChunkEmbeddingEntity.builder()
                .chunk(chunk)
                .embedding(vector)
                .embeddingModel("deterministic-test-model")
                .embeddingDimensions(VECTOR_DIMENSIONS)
                .embeddingVersion("v1")
                .build();
        return EmbeddingResultVO.builder()
                .documentId(documentId)
                .fileName(documentId + ".txt")
                .embeddingModel("deterministic-test-model")
                .embeddingVersion("v1")
                .embeddingDimensions(VECTOR_DIMENSIONS)
                .embeddingCount(1)
                .embeddings(List.of(embedding))
                .build();
    }

    private static float[] unitVector(int index) {
        float[] vector = new float[VECTOR_DIMENSIONS];
        vector[index] = 1.0f;
        return vector;
    }
}
