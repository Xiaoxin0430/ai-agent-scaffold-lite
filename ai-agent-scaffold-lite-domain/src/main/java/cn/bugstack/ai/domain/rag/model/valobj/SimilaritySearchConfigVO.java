package cn.bugstack.ai.domain.rag.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 相似度检索配置。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimilaritySearchConfigVO {

    private Integer defaultTopK;

    private Integer maxTopK;

    private Double defaultMinSimilarity;

}
