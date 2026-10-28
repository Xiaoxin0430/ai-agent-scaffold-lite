package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentCleanerPort;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.CleaningStepVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.TextCleaningTransformer;
import cn.bugstack.ai.types.exception.DocumentCleaningException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Spring AI ETL Transformer 的文档清洗端口实现。
 *
 * @author xiaoxin
 */
@Slf4j
@Service
public class DocumentCleanerPort implements IDocumentCleanerPort {

    public static final String CLEANING_VERSION = "v1";

    private final List<TextCleaningTransformer> transformers;

    public DocumentCleanerPort(List<TextCleaningTransformer> transformers) {
        Objects.requireNonNull(transformers, "transformers cannot be null");
        if (transformers.isEmpty()) {
            throw new IllegalArgumentException("transformers cannot be empty");
        }
        validateTransformers(transformers);
        this.transformers = transformers.stream()
                .sorted(Comparator.comparingInt(TextCleaningTransformer::order))
                .toList();
    }

    @Override
    public CleanedDocumentVO clean(ParsedDocumentVO parsedDocument) {
        Objects.requireNonNull(parsedDocument, "parsedDocument cannot be null");
        //取出文件名和文本
        String fileName = resolveFileName(parsedDocument.getFileName());
        String rawText = parsedDocument.getText() == null ? "" : parsedDocument.getText();
        //包装为SpringAI的Document
        List<Document> documents;
        try {
            documents = List.of(new Document(rawText, toSpringAiMetadata(parsedDocument)));
        } catch (Exception e) {
            throw new DocumentCleaningException(fileName, "pipeline-initialization", e);
        }
        List<CleaningStepVO> cleaningSteps = new ArrayList<>(transformers.size());

        for (TextCleaningTransformer transformer : transformers) {
            String beforeText = singleText(documents, transformer.stepName());
            try {
                documents = transformer.transform(documents);
                String afterText = singleText(documents, transformer.stepName());
                cleaningSteps.add(CleaningStepVO.builder()
                        .stepName(transformer.stepName())
                        .beforeLength(beforeText.length())
                        .afterLength(afterText.length())
                        .changed(!beforeText.equals(afterText))
                        .build());
            } catch (Exception e) {
                throw new DocumentCleaningException(fileName, transformer.stepName(), e);
            }
        }

        CleanedDocumentVO cleanedDocument = CleanedDocumentVO.builder()
                .fileName(fileName)
                .contentType(parsedDocument.getContentType())
                .rawText(rawText)
                .cleanedText(singleText(documents, "pipeline-result"))
                .metadata(copyMetadata(parsedDocument.getMetadata()))
                .cleaningVersion(CLEANING_VERSION)
                .cleaningSteps(List.copyOf(cleaningSteps))
                .build();

        log.info("文档清洗完成 fileName:{} cleaningVersion:{} rawLength:{} cleanedLength:{}",
                cleanedDocument.getFileName(),
                cleanedDocument.getCleaningVersion(),
                cleanedDocument.getRawText().length(),
                cleanedDocument.getCleanedText().length());

        return cleanedDocument;
    }

    private void validateTransformers(List<TextCleaningTransformer> candidates) {
        HashSet<String> stepNames = new HashSet<>();
        HashSet<Integer> orders = new HashSet<>();
        for (TextCleaningTransformer transformer : candidates) {
            Objects.requireNonNull(transformer, "transformer cannot be null");
            if (!stepNames.add(transformer.stepName())) {
                throw new IllegalArgumentException("duplicate cleaning stepName=" + transformer.stepName());
            }
            if (!orders.add(transformer.order())) {
                throw new IllegalArgumentException("duplicate cleaning order=" + transformer.order());
            }
        }
    }

    private String singleText(List<Document> documents, String stepName) {
        if (documents == null || documents.size() != 1) {
            throw new IllegalStateException(
                    "text cleaning step must keep exactly one document, stepName=" + stepName);
        }
        String text = documents.get(0).getText();
        return text == null ? "" : text;
    }

    private Map<String, Object> toSpringAiMetadata(ParsedDocumentVO parsedDocument) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (parsedDocument.getMetadata() != null) {
            metadata.putAll(parsedDocument.getMetadata());
        }
        metadata.put("file_name", resolveFileName(parsedDocument.getFileName()));
        if (parsedDocument.getContentType() != null) {
            metadata.put("content_type", parsedDocument.getContentType());
        }
        return metadata;
    }

    private Map<String, List<String>> copyMetadata(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, values) -> copy.put(
                key,
                values == null ? List.of() : List.copyOf(values)));
        return Map.copyOf(copy);
    }

    private String resolveFileName(String fileName) {
        return fileName == null || fileName.isBlank() ? "unknown" : fileName;
    }

}
