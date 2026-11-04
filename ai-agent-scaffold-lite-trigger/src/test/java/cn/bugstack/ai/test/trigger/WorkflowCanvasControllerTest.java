package cn.bugstack.ai.test.trigger;

import cn.bugstack.ai.domain.agent.model.valobj.canvas.CanvasValidationIssueVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.CanvasValidationResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowPublishResultVO;
import cn.bugstack.ai.domain.agent.service.IWorkflowPublishService;
import cn.bugstack.ai.trigger.http.canvas.WorkflowCanvasController;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class WorkflowCanvasControllerTest {
    private static final String JSON = """
            {"schemaVersion":"2.0","workflow":{"id":"wf_demo","name":"演示","description":"说明"},
             "module":{"aiApi":{"baseUrl":"https://api.deepseek.com","apiKey":"${DeepSeek_API:}",
             "completionsPath":"/chat/completions","embeddingsPath":"/embeddings"},
             "chatModel":{"model":"deepseek-flash"}},
             "editor":{"viewport":{"x":0,"y":0,"zoom":1}},
             "nodes":[{"id":"n1","type":"agent","position":{"x":1,"y":2},
             "config":{"name":"analyze","description":"分析","instruction":"分析需求"}}],"edges":[]}
            """;

    private WorkflowCanvasController controller(IWorkflowPublishService service) {
        var controller = new WorkflowCanvasController();
        ReflectionTestUtils.setField(controller, "workflowPublishService", service);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        return controller;
    }

    @Test
    public void publish_returns_registered_workflow_id_without_yaml() throws Exception {
        IWorkflowPublishService service = mock(IWorkflowPublishService.class);
        when(service.publish(any())).thenReturn(new WorkflowPublishResultVO(
                new CanvasValidationResultVO(List.of(), List.of("n1")), "wf_demo"));
        var mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();

        var response = mvc.perform(post("/api/v1/canvas/publish")
                .contentType(MediaType.APPLICATION_JSON).content(JSON)).andReturn().getResponse();

        Assert.assertEquals(200, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("\"code\":\"0000\""));
        Assert.assertTrue(response.getContentAsString().contains("\"workflowId\":\"wf_demo\""));
        Assert.assertFalse(response.getContentAsString().contains("\"yaml\""));
    }

    @Test
    public void publish_returns_validation_issues() throws Exception {
        IWorkflowPublishService service = mock(IWorkflowPublishService.class);
        when(service.publish(any())).thenReturn(new WorkflowPublishResultVO(
                new CanvasValidationResultVO(List.of(CanvasValidationIssueVO.canvas("WORKFLOW_INVALID", "名称无效")), List.of()), null));
        var mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();

        var response = mvc.perform(post("/api/v1/canvas/publish")
                .contentType(MediaType.APPLICATION_JSON).content(JSON)).andReturn().getResponse();

        Assert.assertTrue(response.getContentAsString().contains("\"code\":\"0002\""));
        Assert.assertTrue(response.getContentAsString().contains("WORKFLOW_INVALID"));
    }

    @Test
    public void publish_reports_duplicate_id_and_old_preview_route_is_gone() throws Exception {
        IWorkflowPublishService service = mock(IWorkflowPublishService.class);
        when(service.publish(any())).thenThrow(new AppException(ResponseCode.CONFLICT.getCode(), "工作流 ID 已发布"));
        var mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();

        var duplicate = mvc.perform(post("/api/v1/canvas/publish")
                .contentType(MediaType.APPLICATION_JSON).content(JSON)).andReturn().getResponse();
        var preview = mvc.perform(post("/api/v1/canvas/preview")
                .contentType(MediaType.APPLICATION_JSON).content(JSON)).andReturn().getResponse();

        Assert.assertTrue(duplicate.getContentAsString().contains("\"code\":\"0004\""));
        Assert.assertEquals(404, preview.getStatus());
    }
}
