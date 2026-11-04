package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowPublishResultVO;

public interface IWorkflowPublishService {
    WorkflowPublishResultVO publish(WorkflowCanvasVO canvas) throws Exception;
}
