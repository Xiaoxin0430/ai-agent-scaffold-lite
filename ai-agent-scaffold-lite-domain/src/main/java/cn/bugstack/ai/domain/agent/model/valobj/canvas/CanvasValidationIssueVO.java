package cn.bugstack.ai.domain.agent.model.valobj.canvas;

public record CanvasValidationIssueVO(String code, String message, String nodeId, String edgeId) {
    public static CanvasValidationIssueVO canvas(String code, String message) {
        return new CanvasValidationIssueVO(code, message, null, null);
    }
    public static CanvasValidationIssueVO node(String code, String message, String id) {
        return new CanvasValidationIssueVO(code, message, id, null);
    }
    public static CanvasValidationIssueVO edge(String code, String message, String id) {
        return new CanvasValidationIssueVO(code, message, null, id);
    }
}
