package cn.bugstack.ai.infrastructure.adapter.port.cleaning;

import org.springframework.stereotype.Component;

/**
 * 将不同平台的换行符统一为 LF。
 *
 * @author xiaoxin
 */
@Component
public class LineEndingNormalizationTransformer implements TextCleaningTransformer {

    @Override
    public String stepName() {
        return "line-ending-normalization";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public String transformText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

}
