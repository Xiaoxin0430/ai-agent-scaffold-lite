package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkingCommandEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentEmbeddingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentChunkerPort;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentCleanerPort;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentEmbeddingPort;
import cn.bugstack.ai.infrastructure.adapter.port.TikaDocumentParserPort;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.ChunkOverlapAppender;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.TextBoundarySplitter;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.TokenAwareChunkPacker;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.DocumentQualityValidationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.InvisibleCharacterTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.LineEndingNormalizationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.UnicodeNormalizationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.WhitespaceNormalizationTransformer;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 文档读取、清洗、切分与向量化完整流程测试。
 *
 * @author xiaoxin
 */
public class DocumentReadCleanChunkAndEmbedTest {

    @Test
    public void test_read_clean_chunk_and_embed_document() throws Exception {
        TikaDocumentParserPort parser = new TikaDocumentParserPort();
        DocumentCleanerPort cleaner = new DocumentCleanerPort(List.of(
                new DocumentQualityValidationTransformer(),
                new WhitespaceNormalizationTransformer(),
                new InvisibleCharacterTransformer(),
                new UnicodeNormalizationTransformer(),
                new LineEndingNormalizationTransformer()
        ));
        TokenCountEstimator estimator = new JTokkitTokenCountEstimator(EncodingType.CL100K_BASE);
        DocumentChunkerPort chunker = createDocumentChunker(estimator);
        DocumentEmbeddingPort embeddingPort = new DocumentEmbeddingPort(
                new DeterministicEmbeddingModel(8));

        try (InputStream inputStream = getClass().getResourceAsStream(
                "/rag/employee-expense-reimbursement-guide.txt")) {
            Assert.assertNotNull("测试文件不存在", inputStream);

            ParsedDocumentVO parsed = parser.parse(
                    "employee-expense-reimbursement-guide.txt", inputStream);
            CleanedDocumentVO cleaned = cleaner.clean(parsed);
            ChunkingResultVO chunkingResult = chunker.chunk(DocumentChunkingCommandEntity.builder()
                    .documentId("employee-expense-guide")
                    .cleanedDocument(cleaned)
                    .config(ChunkingConfigVO.builder()
                            .targetTokens(100)
                            .maxTokens(140)
                            .overlapTokens(20)
                            .chunkingVersion("v1")
                            .build())
                    .build());
            EmbeddingResultVO embeddingResult = embeddingPort.embed(
                    DocumentEmbeddingCommandEntity.builder()
                            .chunkingResult(chunkingResult)
                            .config(EmbeddingConfigVO.builder()
                                    .model("deterministic-test-model")
                                    .dimensions(8)
                                    .batchSize(2)
                                    .embeddingVersion("v1")
                                    .build())
                            .build());

            Assert.assertTrue(chunkingResult.getChunkCount() > 0);
            Assert.assertEquals(chunkingResult.getChunkCount(), embeddingResult.getEmbeddingCount());
            Assert.assertEquals(Integer.valueOf(8), embeddingResult.getEmbeddingDimensions());
            Assert.assertTrue(embeddingResult.getEmbeddings().stream()
                    .allMatch(item -> item.getEmbedding().length == 8));
            for (int index = 0; index < chunkingResult.getChunks().size(); index++) {
                Assert.assertEquals(
                        chunkingResult.getChunks().get(index).getChunkId(),
                        embeddingResult.getEmbeddings().get(index).getChunk().getChunkId());
            }
        }
    }

    private DocumentChunkerPort createDocumentChunker(TokenCountEstimator estimator) {
        DocumentChunkerPort chunker = new DocumentChunkerPort();
        ReflectionTestUtils.setField(chunker, "boundarySplitter", new TextBoundarySplitter());
        ReflectionTestUtils.setField(chunker, "chunkPacker", new TokenAwareChunkPacker(estimator));
        ReflectionTestUtils.setField(chunker, "overlapAppender", new ChunkOverlapAppender(estimator));
        return chunker;
    }

    private static class DeterministicEmbeddingModel implements EmbeddingModel {

        private final int dimensions;

        private DeterministicEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (String text : texts) {
                float[] vector = new float[dimensions];
                for (int index = 0; index < text.length(); index++) {
                    vector[index % dimensions] += text.charAt(index) / 1000.0f;
                }
                vectors.add(vector);
            }
            return vectors;
        }

        @Override
        public float[] embed(Document document) {
            return new float[dimensions];
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new UnsupportedOperationException("not required by this test");
        }

    }

}
