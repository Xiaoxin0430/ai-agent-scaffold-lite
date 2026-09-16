package cn.bugstack.ai.domain.agent.adapter.port;

import cn.bugstack.ai.domain.agent.model.valobj.ParsedDocumentVO;

import java.io.InputStream;

/**
 * 文档解析端口。
 *
 * @author xiaoxin
 */
public interface IDocumentParserPort {

    /**
     * 读取文档并提取正文及元数据。
     * 实现方负责关闭传入的输入流。
     *
     * @param fileName    原始文件名，用于辅助识别文档类型
     * @param inputStream 文档输入流
     * @return 文档解析结果
     */
    ParsedDocumentVO parse(String fileName, InputStream inputStream);

}
