package cn.bugstack.ai.trigger.http.canvas;

import cn.bugstack.ai.api.IWorkflowCanvas;
import cn.bugstack.ai.api.dto.CanvasValidationIssueDTO;
import cn.bugstack.ai.api.dto.WorkflowCanvasPublishResponseDTO;
import cn.bugstack.ai.api.dto.WorkflowCanvasRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import cn.bugstack.ai.domain.agent.service.IWorkflowPublishService;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.ArrayList;

/** Publishes a validated canvas as a new runtime workflow. */
@Slf4j
@RestController
@RequestMapping("/api/v1/canvas")
@CrossOrigin(origins = "*")
public class WorkflowCanvasController implements IWorkflowCanvas {

    @Resource
    private IWorkflowPublishService workflowPublishService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    @PostMapping("/publish")
    public Response<WorkflowCanvasPublishResponseDTO> publish(@RequestBody WorkflowCanvasRequestDTO requestDTO) {
        try {
            WorkflowCanvasVO canvas = objectMapper.convertValue(requestDTO, WorkflowCanvasVO.class);
            var result = workflowPublishService.publish(canvas);
            WorkflowCanvasPublishResponseDTO data = new WorkflowCanvasPublishResponseDTO();
            data.setPublished(result.published());
            data.setWorkflowId(result.workflowId());
            List<CanvasValidationIssueDTO> issues = new ArrayList<>();
            for (var issue : result.validation().issues()) {
                CanvasValidationIssueDTO issueDTO = new CanvasValidationIssueDTO();
                issueDTO.setCode(issue.code());
                issueDTO.setMessage(issue.message());
                issueDTO.setNodeId(issue.nodeId());
                issueDTO.setEdgeId(issue.edgeId());
                issues.add(issueDTO);
            }
            data.setIssues(issues);
            ResponseCode status = result.published() ? ResponseCode.SUCCESS : ResponseCode.ILLEGAL_PARAMETER;
            return Response.<WorkflowCanvasPublishResponseDTO>builder()
                    .code(status.getCode())
                    .info(result.published() ? "发布成功" : "画布校验未通过")
                    .data(data)
                    .build();
        } catch (AppException e) {
            return Response.<WorkflowCanvasPublishResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("工作流发布失败", e);
            return Response.<WorkflowCanvasPublishResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("工作流装配失败，请检查服务日志。")
                    .build();
        }
    }
}
