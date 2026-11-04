package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.agent.adapter.port.IPublishedWorkflowConfigPort;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PostgresPublishedWorkflowConfigPort implements IPublishedWorkflowConfigPort {

    private static final String INSERT_SQL = """
            INSERT INTO published_workflow (workflow_id, config_json)
            VALUES (?, ?::jsonb)
            """;
    private static final String FIND_ALL_SQL = """
            SELECT workflow_id, config_json FROM published_workflow ORDER BY workflow_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresPublishedWorkflowConfigPort(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(AiAgentConfigTableVO config) {
        if (config == null || config.getWorkflow() == null || config.getWorkflow().getId() == null) {
            throw new IllegalArgumentException("工作流 ID 不能为空。");
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("工作流配置无法序列化。", e);
        }
        int inserted = jdbcTemplate.update(INSERT_SQL, config.getWorkflow().getId(), json);
        if (inserted != 1) {
            throw new IllegalStateException("工作流配置插入数量异常：" + config.getWorkflow().getId());
        }
    }

    @Override
    public List<AiAgentConfigTableVO> findAll() {
        return jdbcTemplate.query(FIND_ALL_SQL, (resultSet, rowNum) -> {
            String workflowId = resultSet.getString("workflow_id");
            try {
                AiAgentConfigTableVO config = objectMapper.readValue(
                        resultSet.getString("config_json"), AiAgentConfigTableVO.class);
                if (config == null || config.getWorkflow() == null
                        || !workflowId.equals(config.getWorkflow().getId())) {
                    throw new IllegalStateException("已发布工作流 ID 与数据库记录不一致，workflowId:" + workflowId);
                }
                return config;
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("已发布工作流配置无法反序列化，workflowId:"
                        + workflowId, e);
            }
        });
    }
}
