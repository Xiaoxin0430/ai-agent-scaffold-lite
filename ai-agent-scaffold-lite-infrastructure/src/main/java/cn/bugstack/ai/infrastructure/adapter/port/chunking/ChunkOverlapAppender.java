package cn.bugstack.ai.infrastructure.adapter.port.chunking;

import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.model.ChunkDraft;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.model.TextSpan;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 为相邻文本块补充受 token 预算约束的上下文重叠区。
 *
 * @author xiaoxin
 */
@Component
public class ChunkOverlapAppender {

    private static final int BOUNDARY_SEARCH_WINDOW = 32;
    private static final String PREFERRED_BOUNDARIES = "，。！？；,.!?;:";

    private final TokenCountEstimator tokenEstimator;

    public ChunkOverlapAppender(TokenCountEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public List<ChunkDraft> addOverlap(String text,
                                       List<TextSpan> coreSpans,
                                       ChunkingConfigVO config) {
        List<ChunkDraft> drafts = new ArrayList<>(coreSpans.size());
        for (int index = 0; index < coreSpans.size(); index++) {
            TextSpan core = coreSpans.get(index);
            int contextStart = core.startOffset();
            if (index > 0 && config.getOverlapTokens() > 0) {
                contextStart = findContextStart(
                        text,
                        coreSpans.get(index - 1),
                        core,
                        config.getOverlapTokens(),
                        config.getMaxTokens());
            }

            String content = text.substring(contextStart, core.endOffset());
            int tokenCount = tokenEstimator.estimate(content);
            drafts.add(new ChunkDraft(core, contextStart, content, tokenCount));
        }
        return List.copyOf(drafts);
    }

    private int findContextStart(String text,
                                 TextSpan previousCore,
                                 TextSpan currentCore,
                                 int overlapTokens,
                                 int maxTokens) {
        int low = previousCore.startOffset();
        int high = previousCore.endOffset();
        int accepted = currentCore.startOffset();

        while (low <= high) {
            int middle = (low + high) >>> 1;
            int tailTokens = tokenEstimator.estimate(
                    text.substring(middle, previousCore.endOffset()));
            int contentTokens = tokenEstimator.estimate(
                    text.substring(middle, currentCore.endOffset()));

            if (tailTokens <= overlapTokens && contentTokens <= maxTokens) {
                accepted = middle;
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        int tokenBoundary = keepCodePointBoundary(text, accepted);
        int semanticBoundary = moveForwardToBoundary(
                text, tokenBoundary, previousCore.endOffset());

        // 前一块只包含一个句子时，对齐句末会把 overlap 完全清空。
        // 此时保留 token 边界，优先保证相邻上下文确实存在。
        return semanticBoundary < previousCore.endOffset() ? semanticBoundary : tokenBoundary;
    }

    private int moveForwardToBoundary(String text, int start, int limit) {
        int searchEnd = Math.min(limit, start + BOUNDARY_SEARCH_WINDOW);
        for (int index = start; index < searchEnd; index++) {
            char candidate = text.charAt(index);
            if (Character.isWhitespace(candidate)
                    || PREFERRED_BOUNDARIES.indexOf(candidate) >= 0) {
                return index + 1;
            }
        }
        return start;
    }

    private int keepCodePointBoundary(String text, int offset) {
        if (offset > 0 && offset < text.length() && Character.isLowSurrogate(text.charAt(offset))) {
            return offset - 1;
        }
        return offset;
    }

}
