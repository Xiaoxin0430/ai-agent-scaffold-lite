package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 动态团队运行规则装配。
 */
@Configuration
@EnableConfigurationProperties(DynamicTeamProperties.class)
public class DynamicTeamConfig {

    @Bean
    public DynamicTeamRuntimePolicyVO dynamicTeamRuntimePolicy(
            DynamicTeamProperties properties) {
        validate(properties);

        return DynamicTeamRuntimePolicyVO.builder()
                .enabled(properties.isEnabled())
                .maxWorkers(properties.getMaxWorkers())
                .maxTasks(properties.getMaxTasks())
                .maxConcurrentWorkers(properties.getMaxConcurrentWorkers())
                .maxReplans(properties.getMaxReplans())
                .stepTimeoutSeconds(properties.getStepTimeoutSeconds())
                .runTimeoutSeconds(properties.getRunTimeoutSeconds())
                .maxModelCalls(properties.getMaxModelCalls())
                .maxUserInputCharacters(properties.getMaxUserInputCharacters())
                .build();
    }

    private void validate(DynamicTeamProperties properties) {
        requirePositive("max-workers", properties.getMaxWorkers());
        requirePositive("max-tasks", properties.getMaxTasks());
        requirePositive("max-concurrent-workers", properties.getMaxConcurrentWorkers());
        if (properties.getMaxReplans() < 0) {
            throw new IllegalArgumentException("ai.agent.dynamic-team.max-replans must be non-negative");
        }
        requirePositive("step-timeout-seconds", properties.getStepTimeoutSeconds());
        requirePositive("run-timeout-seconds", properties.getRunTimeoutSeconds());
        requirePositive("max-model-calls", properties.getMaxModelCalls());
        requirePositive("max-user-input-characters", properties.getMaxUserInputCharacters());

        if (properties.getMaxConcurrentWorkers() > properties.getMaxWorkers()) {
            throw new IllegalArgumentException(
                    "ai.agent.dynamic-team.max-concurrent-workers cannot exceed max-workers");
        }
    }

    private void requirePositive(String propertyName, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "ai.agent.dynamic-team." + propertyName + " must be positive");
        }
    }

}
