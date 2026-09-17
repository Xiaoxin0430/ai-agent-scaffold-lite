package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentChunkerPort;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkingCommandEntity;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.domain.rag.model.valobj.ChunkingResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.ChunkOverlapAppender;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.TextBoundarySplitter;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.TokenAwareChunkPacker;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.model.ChunkDraft;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.model.TextSpan;
import cn.bugstack.ai.types.exception.DocumentChunkingException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 段落感知、token 受限的文档切分端口实现。
 *
 * @author xiaoxin
 */
@Slf4j
@Service
public class DocumentChunkerPort implements IDocumentChunkerPort {

    @Resource
    private TextBoundarySplitter boundarySplitter;

    @Resource
    private TokenAwareChunkPacker chunkPacker;

    @Resource
    private ChunkOverlapAppender overlapAppender;

    @Override
    public ChunkingResultVO chunk(DocumentChunkingCommandEntity command) {
        String documentId = resolveDocumentId(command);
        String stepName = "input-validation";
        try {
            validateCommand(command);

            CleanedDocumentVO document = command.getCleanedDocument();
            ChunkingConfigVO config = command.getConfig();
            String text = document.getCleanedText();

            stepName = "paragraph-segmentation";
            List<TextSpan> paragraphs = boundarySplitter.splitParagraphs(text);

            stepName = "paragraph-packing";
            List<TextSpan> paragraphBlocks = chunkPacker.packParagraphs(text, paragraphs, config);

            stepName = "sentence-refinement";
            List<TextSpan> semanticChunks = refineOversizedBlocks(text, paragraphBlocks, config);

            stepName = "hard-split";
            List<TextSpan> boundedChunks = chunkPacker.hardSplit(text, semanticChunks, config);

            stepName = "overlap";
            List<ChunkDraft> drafts = overlapAppender.addOverlap(text, boundedChunks, config);

            stepName = "entity-assembly";
            List<DocumentChunkEntity> chunks = buildChunkEntities(command, drafts);

            stepName = "result-validation";
            validateResult(text, chunks, config);

            log.info("文档切分完成 documentId:{} fileName:{} chunkingVersion:{} chunkCount:{}",
                    documentId,
                    document.getFileName(),
                    config.getChunkingVersion(),
                    chunks.size());

            return ChunkingResultVO.builder()
                    .documentId(documentId)
                    .fileName(document.getFileName())
                    .chunkingVersion(config.getChunkingVersion())
                    .chunkCount(chunks.size())
                    .chunks(List.copyOf(chunks))
                    .build();
        } catch (Exception e) {
            throw new DocumentChunkingException(documentId, stepName, e);
        }
    }

    private List<TextSpan> refineOversizedBlocks(String text,
                                                  List<TextSpan> paragraphBlocks,
                                                  ChunkingConfigVO config) {
        List<TextSpan> refined = new ArrayList<>();
        for (TextSpan block : paragraphBlocks) {
            if (chunkPacker.countTokens(text, block) <= config.getTargetTokens()) {
                refined.add(block);
                continue;
            }

            List<TextSpan> sentences = boundarySplitter.splitSentences(text, block);
            refined.addAll(chunkPacker.packSentences(text, sentences, config));
        }
        return List.copyOf(refined);
    }

    private List<DocumentChunkEntity> buildChunkEntities(DocumentChunkingCommandEntity command,
                                                          List<ChunkDraft> drafts) {
        CleanedDocumentVO document = command.getCleanedDocument();
        ChunkingConfigVO config = command.getConfig();
        List<DocumentChunkEntity> chunks = new ArrayList<>(drafts.size());

        for (int index = 0; index < drafts.size(); index++) {
            ChunkDraft draft = drafts.get(index);
            String chunkId = createChunkId(command, draft, index);
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (document.getMetadata() != null) {
                metadata.putAll(document.getMetadata());
            }
            metadata.put("document_id", command.getDocumentId());
            metadata.put("chunk_id", chunkId);
            metadata.put("chunk_index", index);
            metadata.put("file_name", document.getFileName());
            if (document.getContentType() != null) {
                metadata.put("content_type", document.getContentType());
            }
            metadata.put("context_start_offset", draft.contextStartOffset());
            metadata.put("core_start_offset", draft.coreSpan().startOffset());
            metadata.put("core_end_offset", draft.coreSpan().endOffset());
            metadata.put("token_count", draft.tokenCount());
            metadata.put("cleaning_version", document.getCleaningVersion());
            metadata.put("chunking_version", config.getChunkingVersion());

            chunks.add(DocumentChunkEntity.builder()
                    .chunkId(chunkId)
                    .documentId(command.getDocumentId())
                    .chunkIndex(index)
                    .content(draft.content())
                    .tokenCount(draft.tokenCount())
                    .contextStartOffset(draft.contextStartOffset())
                    .coreStartOffset(draft.coreSpan().startOffset())
                    .coreEndOffset(draft.coreSpan().endOffset())
                    .cleaningVersion(document.getCleaningVersion())
                    .chunkingVersion(config.getChunkingVersion())
                    .metadata(Map.copyOf(metadata))
                    .build());
        }
        return List.copyOf(chunks);
    }

    private String createChunkId(DocumentChunkingCommandEntity command,
                                 ChunkDraft draft,
                                 int chunkIndex) {
        String identity = command.getDocumentId()
                + ":" + command.getCleanedDocument().getCleaningVersion()
                + ":" + command.getConfig().getChunkingVersion()
                + ":" + chunkIndex
                + ":" + draft.content();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void validateCommand(DocumentChunkingCommandEntity command) {
        Objects.requireNonNull(command, "command cannot be null");
        if (isBlank(command.getDocumentId())) {
            throw new IllegalArgumentException("documentId cannot be blank");
        }

        CleanedDocumentVO document = Objects.requireNonNull(
                command.getCleanedDocument(), "cleanedDocument cannot be null");
        if (isBlank(document.getCleanedText())) {
            throw new IllegalArgumentException("cleanedText cannot be blank");
        }
        if (isBlank(document.getCleaningVersion())) {
            throw new IllegalArgumentException("cleaningVersion cannot be blank");
        }

        ChunkingConfigVO config = Objects.requireNonNull(command.getConfig(), "config cannot be null");
        if (config.getTargetTokens() <= 0) {
            throw new IllegalArgumentException("targetTokens must be positive");
        }
        if (config.getMaxTokens() <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (config.getOverlapTokens() < 0 || config.getOverlapTokens() >= config.getMaxTokens()) {
            throw new IllegalArgumentException("overlapTokens must be in [0, maxTokens)");
        }
        if (config.getTargetTokens() > config.getCoreMaxTokens()) {
            throw new IllegalArgumentException("targetTokens must not exceed coreMaxTokens");
        }
        if (isBlank(config.getChunkingVersion())) {
            throw new IllegalArgumentException("chunkingVersion cannot be blank");
        }
    }

    private void validateResult(String text,
                                List<DocumentChunkEntity> chunks,
                                ChunkingConfigVO config) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalStateException("chunk result cannot be empty");
        }

        HashSet<String> chunkIds = new HashSet<>();
        int previousCoreEnd = 0;
        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunkEntity chunk = chunks.get(index);
            if (!Objects.equals(chunk.getChunkIndex(), index)) {
                throw new IllegalStateException("chunk index is not continuous, index=" + index);
            }
            if (isBlank(chunk.getChunkId()) || !chunkIds.add(chunk.getChunkId())) {
                throw new IllegalStateException("chunk id is blank or duplicated, index=" + index);
            }
            if (isBlank(chunk.getContent())) {
                throw new IllegalStateException("chunk content cannot be blank, index=" + index);
            }
            if (chunk.getContextStartOffset() < 0
                    || chunk.getContextStartOffset() > chunk.getCoreStartOffset()
                    || chunk.getCoreStartOffset() >= chunk.getCoreEndOffset()
                    || chunk.getCoreEndOffset() > text.length()) {
                throw new IllegalStateException("invalid chunk offsets, index=" + index);
            }

            String expectedContent = text.substring(
                    chunk.getContextStartOffset(), chunk.getCoreEndOffset());
            if (!expectedContent.equals(chunk.getContent())) {
                throw new IllegalStateException("chunk content and offsets do not match, index=" + index);
            }

            int actualTokens = chunkPacker.countTokens(chunk.getContent());
            if (!Objects.equals(chunk.getTokenCount(), actualTokens)
                    || actualTokens > config.getMaxTokens()) {
                throw new IllegalStateException("invalid chunk token count, index=" + index);
            }

            if (chunk.getCoreStartOffset() < previousCoreEnd) {
                throw new IllegalStateException("core spans overlap, index=" + index);
            }
            String gap = text.substring(previousCoreEnd, chunk.getCoreStartOffset());
            if (!gap.isBlank()) {
                throw new IllegalStateException("meaningful text was lost before chunk, index=" + index);
            }
            previousCoreEnd = chunk.getCoreEndOffset();
        }

        if (!text.substring(previousCoreEnd).isBlank()) {
            throw new IllegalStateException("meaningful text was lost after the last chunk");
        }
    }

    private String resolveDocumentId(DocumentChunkingCommandEntity command) {
        return command == null || isBlank(command.getDocumentId()) ? "unknown" : command.getDocumentId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
