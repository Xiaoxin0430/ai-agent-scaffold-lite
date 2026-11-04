package cn.bugstack.ai.domain.agent.model.valobj.canvas;

import java.util.List;

public record CanvasValidationResultVO(List<CanvasValidationIssueVO> issues, List<String> orderedNodeIds) {
    public boolean valid() { return issues.isEmpty(); }
}
