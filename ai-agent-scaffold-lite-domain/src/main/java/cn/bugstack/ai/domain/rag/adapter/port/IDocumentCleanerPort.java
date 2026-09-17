package cn.bugstack.ai.domain.rag.adapter.port;

import cn.bugstack.ai.domain.rag.model.valobj.CleanedDocumentVO;
import cn.bugstack.ai.domain.rag.model.valobj.ParsedDocumentVO;

/**
 * 文档清洗端口。
 *
 * @author xiaoxin
 */
public interface IDocumentCleanerPort {

    /**
     * 对解析后的文档进行确定性的文本清洗，原始文本保持不变。
     *
     * @param parsedDocument 文档解析结果
     * @return 文档清洗结果
     */
    CleanedDocumentVO clean(ParsedDocumentVO parsedDocument);

}
