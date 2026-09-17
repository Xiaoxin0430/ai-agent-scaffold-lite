package cn.bugstack.ai.infrastructure.adapter.port.cleaning;

import org.springframework.stereotype.Component;

/**
 * 校验清洗结果是否具备进入后续 RAG 流程的基本质量。
 *
 * @author xiaoxin
 */
@Component
public class DocumentQualityValidationTransformer implements TextCleaningTransformer {

    @Override
    public String stepName() {
        return "document-quality-validation";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public String transformText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("cleaned document text cannot be blank");
        }
        return text;
    }

}
