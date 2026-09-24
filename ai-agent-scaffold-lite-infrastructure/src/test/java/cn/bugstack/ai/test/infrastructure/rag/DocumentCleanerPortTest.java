package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentCleanerPort;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.DocumentQualityValidationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.InvisibleCharacterTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.LineEndingNormalizationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.TextCleaningTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.UnicodeNormalizationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.WhitespaceNormalizationTransformer;
import cn.bugstack.ai.types.exception.DocumentCleaningException;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * 文档文本清洗测试。
 *
 * @author xiaoxin
 */
public class DocumentCleanerPortTest {

    private final DocumentCleanerPort documentCleaner = new DocumentCleanerPort(List.of(
            new DocumentQualityValidationTransformer(),
            new WhitespaceNormalizationTransformer(),
            new InvisibleCharacterTransformer(),
            new UnicodeNormalizationTransformer(),
            new LineEndingNormalizationTransformer()
    ));

    @Test
    public void test_clean_document() {
        String rawText = "\uFEFFCafe\u0301\u200B  测试\r\n\r\n\r\n第二\t行\u0000  ";
        ParsedDocumentVO parsedDocument = ParsedDocumentVO.builder()
                .fileName("sample.txt")
                .contentType("text/plain")
                .text(rawText)
                .metadata(Map.of("author", List.of("tester")))
                .build();

        CleanedDocumentVO result = documentCleaner.clean(parsedDocument);

        Assert.assertEquals(rawText, result.getRawText());
        Assert.assertEquals("Café 测试\n\n第二 行", result.getCleanedText());
        Assert.assertEquals(DocumentCleanerPort.CLEANING_VERSION, result.getCleaningVersion());
        Assert.assertEquals(5, result.getCleaningSteps().size());
        Assert.assertEquals("line-ending-normalization", result.getCleaningSteps().get(0).getStepName());
        Assert.assertEquals("document-quality-validation", result.getCleaningSteps().get(4).getStepName());
        Assert.assertFalse(result.getCleaningSteps().get(4).isChanged());
        Assert.assertEquals(List.of("tester"), result.getMetadata().get("author"));
    }

    @Test
    public void test_clean_is_idempotent() {
        ParsedDocumentVO firstInput = ParsedDocumentVO.builder()
                .fileName("sample.txt")
                .contentType("text/plain")
                .text("  第一行  \r\n\r\n\r\n第二行  ")
                .metadata(Map.of())
                .build();

        CleanedDocumentVO firstResult = documentCleaner.clean(firstInput);
        ParsedDocumentVO secondInput = ParsedDocumentVO.builder()
                .fileName(firstResult.getFileName())
                .contentType(firstResult.getContentType())
                .text(firstResult.getCleanedText())
                .metadata(firstResult.getMetadata())
                .build();

        CleanedDocumentVO secondResult = documentCleaner.clean(secondInput);

        Assert.assertEquals(firstResult.getCleanedText(), secondResult.getCleanedText());
    }

    @Test
    public void test_clean_rejects_blank_document() {
        ParsedDocumentVO parsedDocument = ParsedDocumentVO.builder()
                .fileName("blank.txt")
                .contentType("text/plain")
                .text(" \r\n\t ")
                .metadata(Map.of())
                .build();

        try {
            documentCleaner.clean(parsedDocument);
            Assert.fail("空白文档应当清洗失败");
        } catch (DocumentCleaningException e) {
            Assert.assertEquals("blank.txt", e.getFileName());
            Assert.assertEquals("document-quality-validation", e.getStepName());
        }
    }

}
