package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentEmbeddingPort;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEmbeddingEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentEmbeddingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingResultVO;
import cn.bugstack.ai.types.exception.DocumentEmbeddingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Spring AI {@link EmbeddingModel} 的文档向量化端口实现。
 *
 * @author xiaoxin
 */
@Slf4j
@Service
public class DocumentEmbeddingPort implements IDocumentEmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public DocumentEmbeddingPort(
            @Qualifier("ragEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public EmbeddingResultVO embed(DocumentEmbeddingCommandEntity command) {
        String documentId = resolveDocumentId(command);
        String stepName = "input-validation";
        try {
            validateCommand(command);

            ChunkingResultVO chunkingResult = command.getChunkingResult();
            EmbeddingConfigVO config = command.getConfig();
            List<DocumentChunkEntity> chunks = chunkingResult.getChunks();
            List<DocumentChunkEmbeddingEntity> embeddings = new ArrayList<>(chunks.size());

            stepName = "batch-embedding";
            for (int fromIndex = 0; fromIndex < chunks.size(); fromIndex += config.getBatchSize()) {
                int toIndex = Math.min(fromIndex + config.getBatchSize(), chunks.size());
                List<DocumentChunkEntity> batch = chunks.subList(fromIndex, toIndex);
                List<String> texts = batch.stream()
                        .map(DocumentChunkEntity::getContent)
                        .toList();
                List<float[]> vectors = embeddingModel.embed(texts);

                stepName = "result-mapping";
                if (vectors == null || vectors.size() != batch.size()) {
                    throw new IllegalStateException(
                            "embedding result count does not match input count, expected="
                                    + batch.size() + ", actual="
                                    + (vectors == null ? "null" : vectors.size()));
                }

                for (int index = 0; index < batch.size(); index++) {
                    float[] vector = vectors.get(index);
                    validateVector(vector, config, fromIndex + index);
                    embeddings.add(DocumentChunkEmbeddingEntity.builder()
                            .chunk(batch.get(index))
                            .embedding(vector.clone())
                            .embeddingModel(config.getModel())
                            .embeddingDimensions(vector.length)
                            .embeddingVersion(config.getEmbeddingVersion())
                            .build());
                }
                stepName = "batch-embedding";
            }

            stepName = "result-validation";
            validateResult(chunks, embeddings);

            log.info(
                    "文档向量化完成 documentId:{} fileName:{} model:{} dimensions:{} embeddingVersion:{} embeddingCount:{}",
                    documentId,
                    chunkingResult.getFileName(),
                    config.getModel(),
                    config.getDimensions(),
                    config.getEmbeddingVersion(),
                    embeddings.size());

            return EmbeddingResultVO.builder()
                    .documentId(documentId)
                    .fileName(chunkingResult.getFileName())
                    .embeddingModel(config.getModel())
                    .embeddingVersion(config.getEmbeddingVersion())
                    .embeddingDimensions(config.getDimensions())
                    .embeddingCount(embeddings.size())
                    .embeddings(List.copyOf(embeddings))
                    .build();
        } catch (Exception e) {
            throw new DocumentEmbeddingException(documentId, stepName, e);
        }
    }

    private void validateCommand(DocumentEmbeddingCommandEntity command) {
        Objects.requireNonNull(command, "command cannot be null");
        ChunkingResultVO chunkingResult = Objects.requireNonNull(
                command.getChunkingResult(), "chunkingResult cannot be null");
        if (isBlank(chunkingResult.getDocumentId())) {
            throw new IllegalArgumentException("documentId cannot be blank");
        }
        if (chunkingResult.getChunks() == null || chunkingResult.getChunks().isEmpty()) {
            throw new IllegalArgumentException("chunks cannot be empty");
        }
        for (int index = 0; index < chunkingResult.getChunks().size(); index++) {
            DocumentChunkEntity chunk = Objects.requireNonNull(
                    chunkingResult.getChunks().get(index), "chunk cannot be null, index=" + index);
            if (isBlank(chunk.getChunkId())) {
                throw new IllegalArgumentException("chunkId cannot be blank, index=" + index);
            }
            if (isBlank(chunk.getContent())) {
                throw new IllegalArgumentException("chunk content cannot be blank, index=" + index);
            }
        }

        EmbeddingConfigVO config = Objects.requireNonNull(command.getConfig(), "config cannot be null");
        if (isBlank(config.getModel())) {
            throw new IllegalArgumentException("embedding model cannot be blank");
        }
        if (config.getDimensions() <= 0) {
            throw new IllegalArgumentException("embedding dimensions must be positive");
        }
        if (config.getBatchSize() <= 0) {
            throw new IllegalArgumentException("embedding batchSize must be positive");
        }
        if (isBlank(config.getEmbeddingVersion())) {
            throw new IllegalArgumentException("embeddingVersion cannot be blank");
        }
    }

    private void validateVector(float[] vector, EmbeddingConfigVO config, int chunkIndex) {
        if (vector == null) {
            throw new IllegalStateException("embedding vector cannot be null, chunkIndex=" + chunkIndex);
        }
        if (vector.length != config.getDimensions()) {
            throw new IllegalStateException(
                    "embedding dimensions do not match, chunkIndex=" + chunkIndex
                            + ", expected=" + config.getDimensions()
                            + ", actual=" + vector.length);
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException(
                        "embedding vector contains a non-finite value, chunkIndex=" + chunkIndex);
            }
        }
    }

    private void validateResult(List<DocumentChunkEntity> chunks,
                                List<DocumentChunkEmbeddingEntity> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalStateException("embedding result count does not match chunk count");
        }
        for (int index = 0; index < chunks.size(); index++) {
            if (!Objects.equals(
                    chunks.get(index).getChunkId(),
                    embeddings.get(index).getChunk().getChunkId())) {
                throw new IllegalStateException("chunk order changed after embedding, index=" + index);
            }
        }
    }

    private String resolveDocumentId(DocumentEmbeddingCommandEntity command) {
        if (command == null || command.getChunkingResult() == null
                || isBlank(command.getChunkingResult().getDocumentId())) {
            return "unknown";
        }
        return command.getChunkingResult().getDocumentId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
