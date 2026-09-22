package cn.bugstack.ai.types.exception;

/**
 * 文档向量化异常。
 *
 * @author xiaoxin
 */
public class DocumentEmbeddingException extends RuntimeException {

    private final String documentId;
    private final String stepName;

    public DocumentEmbeddingException(String documentId, String stepName, Throwable cause) {
        super("文档向量化失败，documentId=" + documentId + "，stepName=" + stepName, cause);
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
