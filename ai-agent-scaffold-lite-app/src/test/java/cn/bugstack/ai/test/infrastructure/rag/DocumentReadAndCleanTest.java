package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentParserPort;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentCleanerPort;
import cn.bugstack.ai.infrastructure.adapter.port.TikaDocumentParserPort;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.DocumentQualityValidationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.InvisibleCharacterTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.LineEndingNormalizationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.UnicodeNormalizationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.WhitespaceNormalizationTransformer;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

/**
 * 文档读取与清洗完整流程测试。
 *
 * @author xiaoxin
 */
public class DocumentReadAndCleanTest {

    private final IDocumentParserPort documentParser = new TikaDocumentParserPort();

    private final DocumentCleanerPort documentCleaner = new DocumentCleanerPort(List.of(
            new DocumentQualityValidationTransformer(),
            new WhitespaceNormalizationTransformer(),
            new InvisibleCharacterTransformer(),
            new UnicodeNormalizationTransformer(),
            new LineEndingNormalizationTransformer()
    ));

    @Test
    public void test_read_and_clean_document() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(
                "/rag/employee-expense-reimbursement-guide.txt")) {
            Assert.assertNotNull("测试文件不存在", inputStream);

            ParsedDocumentVO parsedDocument = documentParser.parse(
                    "employee-expense-reimbursement-guide.txt", inputStream);
            CleanedDocumentVO cleanedDocument = documentCleaner.clean(parsedDocument);

            Assert.assertFalse("清洗后的文本不应为空", cleanedDocument.getCleanedText().isBlank());

            System.out.println("========== 清洗后的文档内容 ==========");
            System.out.println(cleanedDocument.getCleaningSteps());
            System.out.println("====================================");
        }
    }

}
