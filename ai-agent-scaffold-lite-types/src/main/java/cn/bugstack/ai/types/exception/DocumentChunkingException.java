package cn.bugstack.ai.types.exception;

/**
 * 文档文本切分异常。
 *
 * @author xiaoxin
 */
public class DocumentChunkingException extends RuntimeException {

    private final String documentId;
    private final String stepName;

    public DocumentChunkingException(String documentId, String stepName, Throwable cause) {
        super("文档切分失败，documentId=" + documentId + "，stepName=" + stepName, cause);
        this.documentId = documentId;
        this.stepName = stepName;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getStepName() {
        return stepName;
    }

}
