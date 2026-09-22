package cn.bugstack.ai.types.exception;

/**
 * 文档向量存储异常。
 *
 * @author xiaoxin
 */
public class DocumentVectorStoreException extends RuntimeException {

    private final String documentId;
    private final String operationName;

    public DocumentVectorStoreException(String documentId, String operationName, Throwable cause) {
        super("文档向量存储失败，documentId=" + documentId + "，operationName=" + operationName, cause);
        this.documentId = documentId;
        this.operationName = operationName;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getOperationName() {
        return operationName;
    }

}
