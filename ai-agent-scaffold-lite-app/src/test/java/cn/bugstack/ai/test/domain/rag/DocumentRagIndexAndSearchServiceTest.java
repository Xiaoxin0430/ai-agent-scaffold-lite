package cn.bugstack.ai.test.domain.rag;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentVectorStorePort;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.entity.SimilaritySearchCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.DocumentIndexResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilaritySearchConfigVO;
import cn.bugstack.ai.domain.rag.service.DocumentRagService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RAG 文档入库与相似度检索领域编排测试。
 *
 * @author xiaoxin
 */
public class DocumentRagIndexAndSearchServiceTest {

    @Test
    public void test_search_uses_defaults_and_passes_query_vector() {
        DocumentRagService service = new DocumentRagService();
        EmbeddingConfigVO embeddingConfig = EmbeddingConfigVO.builder()
                .model("test-model")
                .dimensions(3)
                .batchSize(2)
                .embeddingVersion("v1")
                .build();
        SimilaritySearchConfigVO searchConfig = SimilaritySearchConfigVO.builder()
                .defaultTopK(5)
                .maxTopK(20)
                .defaultMinSimilarity(0.25d)
                .build();
        AtomicReference<SimilaritySearchCommandEntity> commandReference = new AtomicReference<>();
        IDocumentVectorStorePort vectorStorePort = new IDocumentVectorStorePort() {
            @Override
            public DocumentIndexResultVO replaceDocument(EmbeddingResultVO embeddingResult) {
                throw new UnsupportedOperationException("not required by this test");
            }

            @Override
            public List<SimilarDocumentChunkVO> search(SimilaritySearchCommandEntity command) {
                commandReference.set(command);
                return List.of(SimilarDocumentChunkVO.builder()
                        .chunk(DocumentChunkEntity.builder()
                                .chunkId("chunk-1")
                                .documentId("doc-1")
                                .chunkIndex(0)
                                .content("result")
                                .build())
                        .similarity(0.9d)
                        .build());
            }
        };

        ReflectionTestUtils.setField(service, "queryEmbeddingPort",
                (cn.bugstack.ai.domain.rag.adapter.port.IQueryEmbeddingPort)
                        (query, config) -> new float[]{1.0f, 2.0f, 3.0f});
        ReflectionTestUtils.setField(service, "documentVectorStorePort", vectorStorePort);
        ReflectionTestUtils.setField(service, "embeddingConfig", embeddingConfig);
        ReflectionTestUtils.setField(service, "similaritySearchConfig", searchConfig);

        List<SimilarDocumentChunkVO> results = service.searchSimilarChunks(
                "expense policy", null, null, List.of("doc-1"));

        Assert.assertEquals(1, results.size());
        SimilaritySearchCommandEntity command = commandReference.get();
        Assert.assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f},
                command.getQueryEmbedding(), 0.0f);
        Assert.assertEquals(Integer.valueOf(5), command.getTopK());
        Assert.assertEquals(Double.valueOf(0.25d), command.getMinSimilarity());
        Assert.assertEquals(List.of("doc-1"), command.getDocumentIds());
    }

    @Test
    public void test_search_rejects_top_k_above_limit_before_embedding() {
        DocumentRagService service = new DocumentRagService();
        ReflectionTestUtils.setField(service, "similaritySearchConfig",
                SimilaritySearchConfigVO.builder()
                        .defaultTopK(5)
                        .maxTopK(20)
                        .defaultMinSimilarity(0.0d)
                        .build());

        try {
            service.searchSimilarChunks("query", 21, 0.0d, List.of());
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("topK"));
        }
    }

}
