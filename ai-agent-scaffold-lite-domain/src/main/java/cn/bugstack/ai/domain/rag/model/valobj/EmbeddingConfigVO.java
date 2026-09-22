package cn.bugstack.ai.domain.rag.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 文档向量化配置。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmbeddingConfigVO {

    private String model;

    private int dimensions;

    private int batchSize;

    private String embeddingVersion;

}
