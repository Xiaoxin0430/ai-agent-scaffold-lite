package cn.bugstack.ai.infrastructure.adapter.port.cleaning;

import com.ibm.icu.text.Normalizer2;
import org.springframework.stereotype.Component;

/**
 * 使用 Unicode NFC 进行规范化，不执行可能改变含义的兼容字符折叠。
 *
 * @author xiaoxin
 */
@Component
public class UnicodeNormalizationTransformer implements TextCleaningTransformer {

    private static final Normalizer2 NFC_NORMALIZER = Normalizer2.getNFCInstance();

    @Override
    public String stepName() {
        return "unicode-nfc-normalization";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public String transformText(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return NFC_NORMALIZER.normalize(text);
    }

}
