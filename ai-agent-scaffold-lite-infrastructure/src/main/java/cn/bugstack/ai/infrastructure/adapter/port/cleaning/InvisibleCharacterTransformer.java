package cn.bugstack.ai.infrastructure.adapter.port.cleaning;

import org.springframework.stereotype.Component;

/**
 * 清理确定无检索价值的不可见字符和控制字符。
 * ZWJ/ZWNJ 等可能影响语言或 emoji 表达的字符会被保留。
 *
 * @author xiaoxin
 */
@Component
public class InvisibleCharacterTransformer implements TextCleaningTransformer {

    private static final int ZERO_WIDTH_SPACE = 0x200B;
    private static final int WORD_JOINER = 0x2060;
    private static final int BYTE_ORDER_MARK = 0xFEFF;
    private static final int SOFT_HYPHEN = 0x00AD;

    @Override
    public String stepName() {
        return "invisible-character-cleaning";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public String transformText(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> appendCleanCodePoint(result, codePoint));
        return result.toString();
    }

    private void appendCleanCodePoint(StringBuilder result, int codePoint) {
        if (codePoint == '\n' || codePoint == '\t') {
            result.appendCodePoint(codePoint);
            return;
        }

        if (codePoint == '\f' || codePoint == 0x000B) {
            result.append('\n');
            return;
        }

        if (codePoint == ZERO_WIDTH_SPACE
                || codePoint == WORD_JOINER
                || codePoint == BYTE_ORDER_MARK
                || codePoint == SOFT_HYPHEN
                || Character.getType(codePoint) == Character.CONTROL) {
            return;
        }

        result.appendCodePoint(codePoint);
    }

}
