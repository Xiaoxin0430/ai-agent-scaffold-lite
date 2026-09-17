package cn.bugstack.ai.infrastructure.adapter.port.chunking.model;

/**
 * 清洗后文本中的左闭右开字符区间。
 *
 * @param startOffset 起始下标，包含
 * @param endOffset   结束下标，不包含
 * @author xiaoxin
 */
public record TextSpan(int startOffset, int endOffset) {

    public TextSpan {
        if (startOffset < 0 || endOffset <= startOffset) {
            throw new IllegalArgumentException(
                    "invalid text span, startOffset=" + startOffset + ", endOffset=" + endOffset);
        }
    }

}
