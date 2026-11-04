package cn.bugstack.ai.api;

import cn.bugstack.ai.api.dto.WorkflowCanvasPublishResponseDTO;
import cn.bugstack.ai.api.dto.WorkflowCanvasRequestDTO;
import cn.bugstack.ai.api.response.Response;

/** 画布配置对外 HTTP 契约。 */
public interface IWorkflowCanvas {

    Response<WorkflowCanvasPublishResponseDTO> publish(WorkflowCanvasRequestDTO requestDTO);

}
