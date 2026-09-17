package cn.bugstack.ai.domain.rag.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 文档切分配置。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChunkingConfigVO {

    /**
     * 正常切分时尽量接近的 token 数。
     */
    private int targetTokens;

    /**
     * 包含 overlap 后的最终文本块 token 上限。
     */
    private int maxTokens;

    /**
     * 从前一文本块尾部引入的最大 token 数。
     */
    private int overlapTokens;

    private String chunkingVersion;

    /**
     * 为 overlap 预留空间后，核心文本可使用的最大 token 数。
     */
    public int getCoreMaxTokens() {
        return maxTokens - overlapTokens;
    }

}
