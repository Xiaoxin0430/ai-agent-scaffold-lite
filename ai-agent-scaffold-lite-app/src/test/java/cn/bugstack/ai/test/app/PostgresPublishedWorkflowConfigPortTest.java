package cn.bugstack.ai.test.app;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.infrastructure.adapter.repository.PostgresPublishedWorkflowConfigPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

public class PostgresPublishedWorkflowConfigPortTest {

    @Test
    public void insert_serializes_complete_config_with_placeholder() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), eq("wf_new"), anyString())).thenReturn(1);
        PostgresPublishedWorkflowConfigPort port =
                new PostgresPublishedWorkflowConfigPort(jdbcTemplate, new ObjectMapper());
        AiAgentConfigTableVO config = new AiAgentConfigTableVO();
        AiAgentConfigTableVO.Workflow workflow = new AiAgentConfigTableVO.Workflow();
        workflow.setId("wf_new");
        workflow.setName("测试工作流");
        config.setWorkflow(workflow);
        AiAgentConfigTableVO.Module module = new AiAgentConfigTableVO.Module();
        AiAgentConfigTableVO.Module.AiApi api = new AiAgentConfigTableVO.Module.AiApi();
        api.setApiKey("${TEST_CANVAS_KEY:}");
        module.setAiApi(api);
        config.setModule(module);

        port.insert(config);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), eq("wf_new"), json.capture());
        AiAgentConfigTableVO decoded = new ObjectMapper().readValue(json.getValue(), AiAgentConfigTableVO.class);
        Assert.assertEquals("wf_new", decoded.getWorkflow().getId());
        Assert.assertEquals("测试工作流", decoded.getWorkflow().getName());
        Assert.assertEquals("${TEST_CANVAS_KEY:}", decoded.getModule().getAiApi().getApiKey());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void find_all_recreates_config_from_database_json() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        String json = """
                {"workflow":{"id":"wf_saved","name":"已保存"},
                 "module":{"aiApi":{"apiKey":"${TEST_CANVAS_KEY:}"}}}
                """;
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("workflow_id")).thenReturn("wf_saved");
        when(resultSet.getString("config_json")).thenReturn(json);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<AiAgentConfigTableVO> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });
        PostgresPublishedWorkflowConfigPort port =
                new PostgresPublishedWorkflowConfigPort(jdbcTemplate, new ObjectMapper());

        List<AiAgentConfigTableVO> restored = port.findAll();

        Assert.assertEquals(1, restored.size());
        Assert.assertEquals("wf_saved", restored.get(0).getWorkflow().getId());
        Assert.assertEquals("${TEST_CANVAS_KEY:}", restored.get(0).getModule().getAiApi().getApiKey());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void corrupt_database_json_identifies_workflow() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("workflow_id")).thenReturn("wf_broken");
        when(resultSet.getString("config_json")).thenReturn("{broken");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<AiAgentConfigTableVO> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });
        PostgresPublishedWorkflowConfigPort port =
                new PostgresPublishedWorkflowConfigPort(jdbcTemplate, new ObjectMapper());

        try {
            port.findAll();
            Assert.fail("Expected malformed published config");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("wf_broken"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void database_id_must_match_json_workflow_id() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("workflow_id")).thenReturn("wf_row");
        when(resultSet.getString("config_json")).thenReturn("{\"workflow\":{\"id\":\"wf_other\"}}");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<AiAgentConfigTableVO> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });
        PostgresPublishedWorkflowConfigPort port =
                new PostgresPublishedWorkflowConfigPort(jdbcTemplate, new ObjectMapper());

        try {
            port.findAll();
            Assert.fail("Expected mismatched workflow ID rejection");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("wf_row"));
        }
    }
}
