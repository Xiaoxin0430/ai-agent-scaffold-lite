package cn.bugstack.ai.infrastructure.adapter.port.cleaning;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.List;
import java.util.Objects;

/**
 * 文本清洗 Transformer 的统一约束。
 *
 * @author xiaoxin
 */
public interface TextCleaningTransformer extends DocumentTransformer {

    /**
     * 清洗步骤的稳定标识，用于审计记录。
     */
    String stepName();

    /**
     * 清洗步骤顺序，数值越小越先执行。
     */
    int order();

    /**
     * 处理单段文本。
     */
    String transformText(String text);

    @Override
    default List<Document> apply(List<Document> documents) {
        Objects.requireNonNull(documents, "documents cannot be null");

        return documents.stream()
                .map(document -> {
                    Objects.requireNonNull(document, "document cannot be null");
                    String transformedText = transformText(document.getText());
                    return document.mutate().text(transformedText).build();
                })
                .toList();
    }

}
