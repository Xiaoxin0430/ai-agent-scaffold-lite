package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasConversionResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;

/** 画布配置校验与运行配置转换服务。 */
public interface IWorkflowCanvasService {

    WorkflowCanvasConversionResultVO validateAndConvert(WorkflowCanvasVO canvas);
}
