package cn.bugstack.ai.infrastructure.adapter.port.chunking;

import cn.bugstack.ai.infrastructure.adapter.port.chunking.model.TextSpan;
import com.ibm.icu.text.BreakIterator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别段落和句子自然边界。
 *
 * @author xiaoxin
 */
@Component
public class TextBoundarySplitter {

    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("\\n[\\t ]*\\n+");

    public List<TextSpan> splitParagraphs(String text) {
        List<TextSpan> paragraphs = new ArrayList<>();
        Matcher matcher = PARAGRAPH_SEPARATOR.matcher(text);
        int start = 0;
        while (matcher.find()) {
            addNonBlankSpan(text, start, matcher.start(), paragraphs);
            start = matcher.end();
        }
        addNonBlankSpan(text, start, text.length(), paragraphs);
        return List.copyOf(paragraphs);
    }

    public List<TextSpan> splitSentences(String text, TextSpan block) {
        String blockText = text.substring(block.startOffset(), block.endOffset());
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(blockText);

        List<TextSpan> sentences = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            addNonBlankSpan(
                    text,
                    block.startOffset() + start,
                    block.startOffset() + end,
                    sentences);
        }
        return sentences.isEmpty() ? List.of(block) : List.copyOf(sentences);
    }

    private void addNonBlankSpan(String text, int start, int end, List<TextSpan> result) {
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start < end) {
            result.add(new TextSpan(start, end));
        }
    }

}
