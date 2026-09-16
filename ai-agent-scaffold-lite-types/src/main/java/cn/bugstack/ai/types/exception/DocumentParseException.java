package cn.bugstack.ai.types.exception;

/**
 * 文档内容解析异常。
 *
 * @author xiaoxin
 */
public class DocumentParseException extends RuntimeException {

    private final String fileName;

    public DocumentParseException(String fileName, Throwable cause) {
        super("文档解析失败，fileName=" + fileName, cause);
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

}
