package cn.bugstack.ai.types.exception;

/**
 * 文档文本清洗异常。
 *
 * @author xiaoxin
 */
public class DocumentCleaningException extends RuntimeException {

    private final String fileName;
    private final String stepName;

    public DocumentCleaningException(String fileName, String stepName, Throwable cause) {
        super("文档清洗失败，fileName=" + fileName + "，stepName=" + stepName, cause);
        this.fileName = fileName;
        this.stepName = stepName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStepName() {
        return stepName;
    }

}
