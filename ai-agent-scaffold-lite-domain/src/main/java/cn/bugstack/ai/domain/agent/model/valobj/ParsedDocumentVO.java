package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文档解析结果值对象。
 *
 * @author xiaoxin
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ParsedDocumentVO {

    /**
     * 原始文件名。
     */
    private String fileName;

    /**
     * 解析器检测出的媒体类型。
     */
    private String contentType;

    /**
     * 从文档中提取的纯文本。
     */
    private String text;

    /**
     * 文档标题、作者、创建时间等原始元数据。
     */
    private Map<String, List<String>> metadata;

}
