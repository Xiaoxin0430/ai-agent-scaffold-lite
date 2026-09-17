package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentChunkerPort;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.ChunkOverlapAppender;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.TextBoundarySplitter;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.TokenAwareChunkPacker;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.model.TextSpan;
import cn.bugstack.ai.types.exception.DocumentChunkingException;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

/**
 * 文档文本切分测试。
 *
 * @author xiaoxin
 */
public class DocumentChunkerPortTest {

    private final TokenCountEstimator tokenEstimator =
            new JTokkitTokenCountEstimator(EncodingType.CL100K_BASE);

    private final TextBoundarySplitter boundarySplitter = new TextBoundarySplitter();

    private final DocumentChunkerPort documentChunker = createDocumentChunker();

    @Test
    public void test_split_paragraphs_keeps_source_offsets() {
        String text = "第一段。\n\n第二段。\n \n第三段。";

        List<TextSpan> paragraphs = boundarySplitter.splitParagraphs(text);

        Assert.assertEquals(3, paragraphs.size());
        Assert.assertEquals("第一段。", substring(text, paragraphs.get(0)));
        Assert.assertEquals("第二段。", substring(text, paragraphs.get(1)));
        Assert.assertEquals("第三段。", substring(text, paragraphs.get(2)));
    }

    private DocumentChunkerPort createDocumentChunker() {
        DocumentChunkerPort chunker = new DocumentChunkerPort();
        ReflectionTestUtils.setField(chunker, "boundarySplitter", boundarySplitter);
        ReflectionTestUtils.setField(chunker, "chunkPacker", new TokenAwareChunkPacker(tokenEstimator));
        ReflectionTestUtils.setField(chunker, "overlapAppender", new ChunkOverlapAppender(tokenEstimator));
        return chunker;
    }

    @Test
    public void test_chunk_merges_short_paragraphs() {
        String text = "RAG 文档读取。\n\n文本清洗。\n\n文本切分。";

        ChunkingResultVO result = documentChunker.chunk(command(
                "doc-short", text, config(100, 120, 20)));

        Assert.assertEquals(Integer.valueOf(1), result.getChunkCount());
        Assert.assertEquals(text, result.getChunks().get(0).getContent());
        Assert.assertEquals(Integer.valueOf(0), result.getChunks().get(0).getCoreStartOffset());
        Assert.assertEquals(Integer.valueOf(text.length()), result.getChunks().get(0).getCoreEndOffset());
    }

    @Test
    public void test_chunk_long_document_with_overlap_and_token_limit() {
        String sentence = "文本切分应当保留完整语义，并为后续向量检索提供稳定输入。";
        String text = sentence.repeat(30);
        ChunkingConfigVO config = config(40, 65, 15);

        ChunkingResultVO result = documentChunker.chunk(command("doc-long", text, config));

        Assert.assertTrue(result.getChunkCount() > 1);
        Assert.assertTrue(result.getChunks().stream()
                .allMatch(chunk -> chunk.getTokenCount() <= config.getMaxTokens()));
        Assert.assertTrue(result.getChunks().stream()
                .skip(1)
                .anyMatch(chunk -> chunk.getContextStartOffset() < chunk.getCoreStartOffset()));
    }

    @Test
    public void test_chunk_hard_splits_text_without_punctuation() {
        String text = "超长无标点文本".repeat(150);
        ChunkingConfigVO config = config(20, 30, 5);

        ChunkingResultVO result = documentChunker.chunk(command("doc-hard-split", text, config));

        Assert.assertTrue(result.getChunkCount() > 1);
        for (DocumentChunkEntity chunk : result.getChunks()) {
            Assert.assertTrue(chunk.getTokenCount() <= config.getMaxTokens());
            Assert.assertFalse(chunk.getContent().isBlank());
        }
    }

    @Test
    public void test_chunk_id_is_stable() {
        String text = "第一段文本。\n\n第二段文本。";
        DocumentChunkingCommandEntity command = command(
                "doc-stable", text, config(10, 20, 5));

        ChunkingResultVO first = documentChunker.chunk(command);
        ChunkingResultVO second = documentChunker.chunk(command);

        Assert.assertEquals(
                first.getChunks().stream().map(DocumentChunkEntity::getChunkId).toList(),
                second.getChunks().stream().map(DocumentChunkEntity::getChunkId).toList());
    }

    @Test
    public void test_chunk_rejects_blank_document() {
        try {
            documentChunker.chunk(command("doc-blank", "  \n ", config(10, 20, 5)));
            Assert.fail("空白文档应当切分失败");
        } catch (DocumentChunkingException e) {
            Assert.assertEquals("doc-blank", e.getDocumentId());
            Assert.assertEquals("input-validation", e.getStepName());
        }
    }

    private DocumentChunkingCommandEntity command(String documentId,
                                                   String text,
                                                   ChunkingConfigVO config) {
        CleanedDocumentVO document = CleanedDocumentVO.builder()
                .fileName("sample.txt")
                .contentType("text/plain")
                .rawText(text)
                .cleanedText(text)
                .metadata(Map.of("author", List.of("tester")))
                .cleaningVersion("v1")
                .cleaningSteps(List.of())
                .build();
        return DocumentChunkingCommandEntity.builder()
                .documentId(documentId)
                .cleanedDocument(document)
                .config(config)
                .build();
    }

    private ChunkingConfigVO config(int targetTokens, int maxTokens, int overlapTokens) {
        return ChunkingConfigVO.builder()
                .targetTokens(targetTokens)
                .maxTokens(maxTokens)
                .overlapTokens(overlapTokens)
                .chunkingVersion("v1")
                .build();
    }

    private String substring(String text, TextSpan span) {
        return text.substring(span.startOffset(), span.endOffset());
    }

}
