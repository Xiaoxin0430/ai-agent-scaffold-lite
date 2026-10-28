package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.rag.adapter.port.IDocumentParserPort;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;
import cn.bugstack.ai.types.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Apache Tika 的文档解析端口实现。
 *
 * @author xiaoxin
 */
@Slf4j
@Service
public class TikaDocumentParserPort implements IDocumentParserPort {

    /**
     * 单个文档最多提取一千万字符，防止异常文件无限占用内存。
     */
    private static final int MAX_TEXT_LENGTH = 10_000_000;

    @Override
    public ParsedDocumentVO parse(String fileName, InputStream inputStream) {
        //输入检验
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream cannot be null");
        }
        //准备文件名与元数据
        String resolvedFileName = fileName == null || fileName.isBlank() ? "unknown" : fileName;
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, resolvedFileName);
        //准备文字接收器
        BodyContentHandler contentHandler = new BodyContentHandler(MAX_TEXT_LENGTH);
        //解析文件
        try (TikaInputStream tikaInputStream = TikaInputStream.get(inputStream)) {
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(tikaInputStream, contentHandler, metadata, new ParseContext());

            ParsedDocumentVO parsedDocument = ParsedDocumentVO.builder()
                    .fileName(resolvedFileName)
                    .contentType(metadata.get(Metadata.CONTENT_TYPE))
                    .text(contentHandler.toString())
                    .metadata(convertMetadata(metadata))
                    .build();

            log.info("文档解析完成 fileName:{} contentType:{} textLength:{}",
                    parsedDocument.getFileName(),
                    parsedDocument.getContentType(),
                    parsedDocument.getText().length());

            return parsedDocument;
        } catch (IOException | SAXException | TikaException e) {
            throw new DocumentParseException(resolvedFileName, e);
        }
    }

    private Map<String, List<String>> convertMetadata(Metadata metadata) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            result.put(name, Arrays.asList(metadata.getValues(name)));
        }
        return result;
    }

}
