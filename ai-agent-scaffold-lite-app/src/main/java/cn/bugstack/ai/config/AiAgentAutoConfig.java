package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentAutoConfigProperties.class)
public class AiAgentAutoConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private IArmoryService armoryService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
            if (tables == null || tables.isEmpty()) {
                log.info("Ai Agent 智能体装配已跳过，未配置智能体");
                return;
            }

            log.info("Ai Agent 智能体装配 count:{} ids:{}",
                    tables.size(),
                    tables.keySet());

            armoryService.acceptArmoryAgents(new ArrayList<>(tables.values()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
