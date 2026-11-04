package cn.bugstack.ai.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowCanvasPublishResponseDTO {
    private boolean published;
    private String workflowId;
    private List<CanvasValidationIssueDTO> issues;
}
