package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentParserPort;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.infrastructure.adapter.port.TikaDocumentParserPort;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;

/**
 * Apache Tika 文档解析测试。
 *
 * @author xiaoxin
 */
public class TikaDocumentParserPortTest {

    private final IDocumentParserPort documentParser = new TikaDocumentParserPort();

    @Test
    public void test_parse_text_file() throws Exception {
        try (
                InputStream inputStream = getClass().getResourceAsStream
                        ("/rag/test.pdf")
        ) {

            Assert.assertNotNull("测试文件不存在", inputStream);

            ParsedDocumentVO parsedDocumentVO = documentParser.parse("test.pdf", inputStream);

            System.out.println(parsedDocumentVO.getText());
            System.out.println("\n");
            System.out.println(parsedDocumentVO.getMetadata());

        }
    }

}
