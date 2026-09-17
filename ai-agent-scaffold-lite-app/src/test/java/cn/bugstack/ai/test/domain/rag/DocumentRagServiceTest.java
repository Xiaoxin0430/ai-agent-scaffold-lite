package cn.bugstack.ai.test.domain.rag;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentCleanerPort;
import cn.bugstack.ai.domain.rag.adapter.port.IDocumentParserPort;
import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.domain.rag.service.DocumentRagService;
import cn.bugstack.ai.infrastructure.adapter.port.DocumentCleanerPort;
import cn.bugstack.ai.infrastructure.adapter.port.TikaDocumentParserPort;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.DocumentQualityValidationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.InvisibleCharacterTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.LineEndingNormalizationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.UnicodeNormalizationTransformer;
import cn.bugstack.ai.infrastructure.adapter.port.cleaning.WhitespaceNormalizationTransformer;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档处理领域服务测试。
 *
 * @author xiaoxin
 */
public class DocumentRagServiceTest {

    @Test
    public void test_parse_and_clean() {
        IDocumentParserPort parserPort = (fileName, inputStream) -> ParsedDocumentVO.builder()
                .fileName(fileName)
                .contentType("text/plain")
                .text("raw text")
                .metadata(Map.of())
                .build();

        IDocumentCleanerPort cleanerPort = parsedDocument -> CleanedDocumentVO.builder()
                .fileName(parsedDocument.getFileName())
                .contentType(parsedDocument.getContentType())
                .rawText(parsedDocument.getText())
                .cleanedText("cleaned text")
                .metadata(parsedDocument.getMetadata())
                .cleaningVersion("v1")
                .cleaningSteps(List.of())
                .build();

        DocumentRagService documentRagService = createDocumentRagService(parserPort, cleanerPort);
        CleanedDocumentVO result = documentRagService.parseAndClean(
                "sample.txt",
                new ByteArrayInputStream("raw text".getBytes(StandardCharsets.UTF_8)));

        Assert.assertEquals("sample.txt", result.getFileName());
        Assert.assertEquals("raw text", result.getRawText());
        Assert.assertEquals("cleaned text", result.getCleanedText());
    }

    @Test
    public void test_parse_and_clean_text_file() throws Exception {
        DocumentCleanerPort cleanerPort = new DocumentCleanerPort(List.of(
                new DocumentQualityValidationTransformer(),
                new WhitespaceNormalizationTransformer(),
                new InvisibleCharacterTransformer(),
                new UnicodeNormalizationTransformer(),
                new LineEndingNormalizationTransformer()
        ));
        DocumentRagService documentRagService = createDocumentRagService(
                new TikaDocumentParserPort(), cleanerPort);

        try (InputStream inputStream = getClass().getResourceAsStream("/rag/tika-sample.txt")) {
            Assert.assertNotNull("测试文件不存在", inputStream);

            CleanedDocumentVO result = documentRagService.parseAndClean("tika-sample.txt", inputStream);

            Assert.assertTrue(result.getContentType().startsWith("text/plain"));
            Assert.assertEquals(
                    "RAG测试文档\n\n这是用于验证 Apache Tika 文本提取能力的测试内容。",
                    result.getCleanedText());
            Assert.assertEquals(5, result.getCleaningSteps().size());
        }
    }

    private DocumentRagService createDocumentRagService(IDocumentParserPort parserPort,
                                                         IDocumentCleanerPort cleanerPort) {
        DocumentRagService documentRagService = new DocumentRagService();
        ReflectionTestUtils.setField(documentRagService, "documentParserPort", parserPort);
        ReflectionTestUtils.setField(documentRagService, "documentCleanerPort", cleanerPort);
        return documentRagService;
    }

}
