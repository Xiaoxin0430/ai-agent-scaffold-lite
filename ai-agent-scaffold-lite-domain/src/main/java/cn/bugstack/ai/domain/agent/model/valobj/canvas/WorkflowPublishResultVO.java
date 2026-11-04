package cn.bugstack.ai.domain.agent.model.valobj.canvas;

public record WorkflowPublishResultVO(CanvasValidationResultVO validation, String workflowId) {
    public boolean published() {
        return workflowId != null;
    }
}
