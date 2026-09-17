package cn.bugstack.ai.domain.rag.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文档清洗结果值对象。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CleanedDocumentVO {

    private String fileName;

    private String contentType;

    /**
     * 解析器输出的原始文本，用于审计与重新清洗。
     */
    private String rawText;

    /**
     * 清洗后的文本，用于后续 Chunking。
     */
    private String cleanedText;

    private Map<String, List<String>> metadata;

    /**
     * 清洗规则版本。
     */
    private String cleaningVersion;

    /**
     * 各清洗步骤的执行记录。
     */
    private List<CleaningStepVO> cleaningSteps;

}
