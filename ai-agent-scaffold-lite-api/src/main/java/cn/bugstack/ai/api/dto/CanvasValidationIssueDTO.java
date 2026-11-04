package cn.bugstack.ai.api.dto;

import lombok.Data;

/** 单个画布校验问题。 */
@Data
public class CanvasValidationIssueDTO {
    private String code;
    private String message;
    private String nodeId;
    private String edgeId;
}
