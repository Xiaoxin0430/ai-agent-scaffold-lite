package cn.bugstack.ai.domain.rag.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 单个文本清洗步骤的执行记录。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CleaningStepVO {

    /**
     * 清洗步骤的稳定标识。
     */
    private String stepName;

    /**
     * 执行前文本长度。
     */
    private int beforeLength;

    /**
     * 执行后文本长度。
     */
    private int afterLength;

    /**
     * 本步骤是否改变了文本。
     */
    private boolean changed;

}
