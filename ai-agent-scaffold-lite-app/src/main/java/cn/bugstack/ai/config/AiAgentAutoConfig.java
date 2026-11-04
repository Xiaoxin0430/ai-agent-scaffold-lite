package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.adapter.port.IPublishedWorkflowConfigPort;
import cn.bugstack.ai.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import cn.bugstack.ai.domain.agent.service.canvas.WorkflowRuntimeConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentAutoConfigProperties.class)
public class AiAgentAutoConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private IArmoryService armoryService;

    @Resource
    private IPublishedWorkflowConfigPort publishedWorkflowConfigPort;

    @Resource
    private WorkflowRuntimeConfigFactory runtimeConfigFactory;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
            if (tables != null && !tables.isEmpty()) {
                log.info("Ai Agent 智能体装配 count:{} ids:{}", tables.size(), tables.keySet());
                armoryService.acceptArmoryAgents(new ArrayList<>(tables.values()));
            }

            List<AiAgentConfigTableVO> published = publishedWorkflowConfigPort.findAll();
            for (AiAgentConfigTableVO stored : published) {
                String workflowId = stored != null && stored.getWorkflow() != null
                        ? stored.getWorkflow().getId() : "未知";
                try {
                    AiAgentConfigTableVO runtime = runtimeConfigFactory.create(stored);
                    armoryService.acceptArmoryAgents(List.of(runtime));
                    log.info("已恢复发布工作流 workflowId:{}", workflowId);
                } catch (Exception e) {
                    throw new IllegalStateException("已发布工作流恢复失败，workflowId:" + workflowId, e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
