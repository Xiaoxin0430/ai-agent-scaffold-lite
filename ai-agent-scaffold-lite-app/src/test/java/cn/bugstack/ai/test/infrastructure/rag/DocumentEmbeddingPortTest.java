package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEmbeddingEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentEmbeddingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentEmbeddingPort;
import cn.bugstack.ai.types.exception.DocumentEmbeddingException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档向量化端口测试。
 *
 * @author xiaoxin
 */
public class DocumentEmbeddingPortTest {

    @Test
    public void test_embed_chunks_in_batches_and_keep_order() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(3);
        DocumentEmbeddingPort port = new DocumentEmbeddingPort(embeddingModel);

        EmbeddingResultVO result = port.embed(command(List.of(
                chunk("chunk-0", 0, "alpha"),
                chunk("chunk-1", 1, "beta"),
                chunk("chunk-2", 2, "gamma")
        ), 3, 2));

        Assert.assertEquals(Integer.valueOf(3), result.getEmbeddingCount());
        Assert.assertEquals(Integer.valueOf(3), result.getEmbeddingDimensions());
        Assert.assertEquals("text-embedding-test", result.getEmbeddingModel());
        Assert.assertEquals("v1", result.getEmbeddingVersion());
        Assert.assertEquals(2, embeddingModel.getBatchCalls());

        List<DocumentChunkEmbeddingEntity> embeddings = result.getEmbeddings();
        Assert.assertEquals("chunk-0", embeddings.get(0).getChunk().getChunkId());
        Assert.assertEquals("chunk-1", embeddings.get(1).getChunk().getChunkId());
        Assert.assertEquals("chunk-2", embeddings.get(2).getChunk().getChunkId());
        Assert.assertArrayEquals(new float[]{5.0f, 0.0f, 3.0f}, embeddings.get(0).getEmbedding(), 0.0f);
        Assert.assertArrayEquals(new float[]{4.0f, 1.0f, 3.0f}, embeddings.get(1).getEmbedding(), 0.0f);
        Assert.assertArrayEquals(new float[]{5.0f, 2.0f, 3.0f}, embeddings.get(2).getEmbedding(), 0.0f);
    }

    @Test
    public void test_reject_dimension_mismatch() {
        DocumentEmbeddingPort port = new DocumentEmbeddingPort(
                new FakeEmbeddingModel(2));

        try {
            port.embed(command(List.of(chunk("chunk-0", 0, "alpha")), 3, 2));
            Assert.fail("expected DocumentEmbeddingException");
        } catch (DocumentEmbeddingException e) {
            Assert.assertEquals("doc-embedding-test", e.getDocumentId());
            Assert.assertEquals("result-mapping", e.getStepName());
            Assert.assertTrue(e.getCause().getMessage().contains("dimensions do not match"));
        }
    }

    @Test
    public void test_reject_non_finite_vector_value() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(3);
        embeddingModel.setReturnNan(true);
        DocumentEmbeddingPort port = new DocumentEmbeddingPort(embeddingModel);

        try {
            port.embed(command(List.of(chunk("chunk-0", 0, "alpha")), 3, 2));
            Assert.fail("expected DocumentEmbeddingException");
        } catch (DocumentEmbeddingException e) {
            Assert.assertTrue(e.getCause().getMessage().contains("non-finite"));
        }
    }

    @Test
    public void test_reject_result_count_mismatch() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(3);
        embeddingModel.setDropLastVector(true);
        DocumentEmbeddingPort port = new DocumentEmbeddingPort(embeddingModel);

        try {
            port.embed(command(List.of(
                    chunk("chunk-0", 0, "alpha"),
                    chunk("chunk-1", 1, "beta")
            ), 3, 2));
            Assert.fail("expected DocumentEmbeddingException");
        } catch (DocumentEmbeddingException e) {
            Assert.assertEquals("result-mapping", e.getStepName());
            Assert.assertTrue(e.getCause().getMessage().contains("result count"));
        }
    }

    @Test
    public void test_embedding_array_is_defensively_copied() {
        DocumentEmbeddingPort port = new DocumentEmbeddingPort(
                new FakeEmbeddingModel(3));

        DocumentChunkEmbeddingEntity result = port.embed(command(
                        List.of(chunk("chunk-0", 0, "alpha")), 3, 2))
                .getEmbeddings()
                .get(0);

        float[] firstRead = result.getEmbedding();
        firstRead[0] = 999.0f;

        Assert.assertEquals(5.0f, result.getEmbedding()[0], 0.0f);
    }

    @Test
    public void test_reject_empty_chunks() {
        DocumentEmbeddingPort port = new DocumentEmbeddingPort(
                new FakeEmbeddingModel(3));

        try {
            port.embed(command(List.of(), 3, 2));
            Assert.fail("expected DocumentEmbeddingException");
        } catch (DocumentEmbeddingException e) {
            Assert.assertEquals("input-validation", e.getStepName());
            Assert.assertTrue(e.getCause().getMessage().contains("chunks cannot be empty"));
        }
    }

    private DocumentEmbeddingCommandEntity command(List<DocumentChunkEntity> chunks,
                                                     int dimensions,
                                                     int batchSize) {
        return DocumentEmbeddingCommandEntity.builder()
                .chunkingResult(ChunkingResultVO.builder()
                        .documentId("doc-embedding-test")
                        .fileName("embedding-test.txt")
                        .chunkingVersion("v1")
                        .chunkCount(chunks.size())
                        .chunks(chunks)
                        .build())
                .config(EmbeddingConfigVO.builder()
                        .model("text-embedding-test")
                        .dimensions(dimensions)
                        .batchSize(batchSize)
                        .embeddingVersion("v1")
                        .build())
                .build();
    }

    private DocumentChunkEntity chunk(String chunkId, int chunkIndex, String content) {
        return DocumentChunkEntity.builder()
                .chunkId(chunkId)
                .documentId("doc-embedding-test")
                .chunkIndex(chunkIndex)
                .content(content)
                .tokenCount(1)
                .contextStartOffset(0)
                .coreStartOffset(0)
                .coreEndOffset(content.length())
                .cleaningVersion("v1")
                .chunkingVersion("v1")
                .build();
    }

    private static class FakeEmbeddingModel implements EmbeddingModel {

        private final int dimensions;
        private int batchCalls;
        private int vectorIndex;
        private boolean returnNan;
        private boolean dropLastVector;

        private FakeEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            batchCalls++;
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (String text : texts) {
                float[] vector = new float[dimensions];
                if (dimensions > 0) {
                    vector[0] = returnNan ? Float.NaN : text.length();
                }
                if (dimensions > 1) {
                    vector[1] = vectorIndex;
                }
                if (dimensions > 2) {
                    vector[2] = dimensions;
                }
                vectorIndex++;
                vectors.add(vector);
            }
            if (dropLastVector && !vectors.isEmpty()) {
                vectors.remove(vectors.size() - 1);
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

        public int getBatchCalls() {
            return batchCalls;
        }

        public void setReturnNan(boolean returnNan) {
            this.returnNan = returnNan;
        }

        public void setDropLastVector(boolean dropLastVector) {
            this.dropLastVector = dropLastVector;
        }

    }

}
