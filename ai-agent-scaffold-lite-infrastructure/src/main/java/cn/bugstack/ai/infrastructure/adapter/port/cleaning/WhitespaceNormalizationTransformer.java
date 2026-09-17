package cn.bugstack.ai.infrastructure.adapter.port.cleaning;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * 整理水平空白、行首尾空白和过多空行。
 *
 * @author xiaoxin
 */
@Component
public class WhitespaceNormalizationTransformer implements TextCleaningTransformer {

    private static final Pattern HORIZONTAL_WHITESPACE_PATTERN = Pattern.compile("[\\p{Zs}\\t]+");
    private static final Pattern EXCESSIVE_BLANK_LINES_PATTERN = Pattern.compile("\\n{3,}");

    @Override
    public String stepName() {
        return "whitespace-normalization";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public String transformText(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        String normalizedLines = Arrays.stream(text.split("\\n", -1))
                .map(line -> HORIZONTAL_WHITESPACE_PATTERN.matcher(line).replaceAll(" ").strip())
                .collect(Collectors.joining("\n"));

        return EXCESSIVE_BLANK_LINES_PATTERN.matcher(normalizedLines)
                .replaceAll("\n\n")
                .strip();
    }

}
