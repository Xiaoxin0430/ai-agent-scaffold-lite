package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentChunkerPort;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentCleanerPort;
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
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.List;

/**
 * 文档读取、清洗与切分完整流程测试。
 *
 * @author xiaoxin
 */
public class DocumentReadCleanAndChunkTest {

    @Test
    public void test_read_clean_and_chunk_document() throws Exception {
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

        try (InputStream inputStream = getClass().getResourceAsStream(
                "/rag/employee-expense-reimbursement-guide.txt")) {
            Assert.assertNotNull("测试文件不存在", inputStream);

            ParsedDocumentVO parsed = parser.parse(
                    "employee-expense-reimbursement-guide.txt", inputStream);
            CleanedDocumentVO cleaned = cleaner.clean(parsed);
            ChunkingConfigVO config = ChunkingConfigVO.builder()
                    .targetTokens(100)
                    .maxTokens(140)
                    .overlapTokens(20)
                    .chunkingVersion("v1")
                    .build();

            ChunkingResultVO result = chunker.chunk(DocumentChunkingCommandEntity.builder()
                    .documentId("employee-expense-guide")
                    .cleanedDocument(cleaned)
                    .config(config)
                    .build());

            Assert.assertFalse(cleaned.getCleanedText().isBlank());
            Assert.assertTrue(result.getChunkCount() > 0);
            Assert.assertTrue(result.getChunks().stream()
                    .allMatch(chunk -> chunk.getTokenCount() <= config.getMaxTokens()));
        }
    }

    private DocumentChunkerPort createDocumentChunker(TokenCountEstimator estimator) {
        DocumentChunkerPort chunker = new DocumentChunkerPort();
        ReflectionTestUtils.setField(chunker, "boundarySplitter", new TextBoundarySplitter());
        ReflectionTestUtils.setField(chunker, "chunkPacker", new TokenAwareChunkPacker(estimator));
        ReflectionTestUtils.setField(chunker, "overlapAppender", new ChunkOverlapAppender(estimator));
        return chunker;
    }

}
