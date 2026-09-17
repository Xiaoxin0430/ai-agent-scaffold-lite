package cn.bugstack.ai.infrastructure.adapter.port.chunking;

import cn.bugstack.ai.domain.rag.model.valobj.ChunkingConfigVO;
import cn.bugstack.ai.infrastructure.adapter.port.chunking.model.TextSpan;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 token 预算合并和拆分文本区间。
 *
 * @author xiaoxin
 */
@Component
public class TokenAwareChunkPacker {

    private static final String PREFERRED_BOUNDARIES = "，。！？；,.!?;:";

    private final TokenCountEstimator tokenEstimator;

    public TokenAwareChunkPacker(TokenCountEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public List<TextSpan> packParagraphs(String text,
                                         List<TextSpan> paragraphs,
                                         ChunkingConfigVO config) {
        return packUnits(text, paragraphs, config.getTargetTokens());
    }

    public List<TextSpan> packSentences(String text,
                                        List<TextSpan> sentences,
                                        ChunkingConfigVO config) {
        return packUnits(text, sentences, config.getTargetTokens());
    }

    public List<TextSpan> hardSplit(String text,
                                    List<TextSpan> spans,
                                    ChunkingConfigVO config) {
        List<TextSpan> result = new ArrayList<>();
        int maxTokens = config.getCoreMaxTokens();
        for (TextSpan span : spans) {
            if (countTokens(text, span) <= maxTokens) {
                result.add(span);
            } else {
                result.addAll(hardSplitSpan(text, span, maxTokens));
            }
        }
        return List.copyOf(result);
    }

    public int countTokens(String text) {
        return tokenEstimator.estimate(text);
    }

    public int countTokens(String text, TextSpan span) {
        return countTokens(text.substring(span.startOffset(), span.endOffset()));
    }

    private List<TextSpan> packUnits(String text, List<TextSpan> units, int targetTokens) {
        List<TextSpan> packed = new ArrayList<>();
        TextSpan current = null;
        for (TextSpan unit : units) {
            if (current == null) {
                current = unit;
                continue;
            }

            TextSpan candidate = new TextSpan(current.startOffset(), unit.endOffset());
            if (countTokens(text, candidate) <= targetTokens) {
                current = candidate;
            } else {
                packed.add(current);
                current = unit;
            }
        }
        if (current != null) {
            packed.add(current);
        }
        return List.copyOf(packed);
    }

    private List<TextSpan> hardSplitSpan(String text, TextSpan span, int maxTokens) {
        List<TextSpan> result = new ArrayList<>();
        int start = span.startOffset();
        while (start < span.endOffset()) {
            int maxEnd = findMaxEndByTokens(text, start, span.endOffset(), maxTokens);
            if (maxEnd <= start) {
                maxEnd = Character.offsetByCodePoints(text, start, 1);
            }

            int cut = findPreferredBoundary(text, start, maxEnd);
            cut = keepCodePointBoundary(text, start, cut);
            result.add(new TextSpan(start, cut));

            start = cut;
            while (start < span.endOffset() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return result;
    }

    private int findMaxEndByTokens(String text, int start, int end, int maxTokens) {
        int low = start + 1;
        int high = end;
        int accepted = start;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (countTokens(text.substring(start, middle)) <= maxTokens) {
                accepted = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return keepCodePointBoundary(text, start, accepted);
    }

    private int findPreferredBoundary(String text, int start, int maxEnd) {
        int minimum = start + Math.max(1, (maxEnd - start) * 3 / 5);
        for (int index = maxEnd; index > minimum; index--) {
            char candidate = text.charAt(index - 1);
            if (Character.isWhitespace(candidate)
                    || PREFERRED_BOUNDARIES.indexOf(candidate) >= 0) {
                return index;
            }
        }
        return maxEnd;
    }

    private int keepCodePointBoundary(String text, int start, int end) {
        if (end > start && end < text.length() && Character.isLowSurrogate(text.charAt(end))) {
            return end - 1;
        }
        return end;
    }

}
