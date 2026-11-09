package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamPromptBundleVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 动态团队固定控制提示词装配。
 */
@Configuration
public class DynamicTeamPromptConfig {

    private static final String PROMPT_VERSION = "dynamic-team-prompts-v1";
    private static final String PROMPT_DIRECTORY = "agent/dynamic-team/prompts/v1/";

    @Bean
    public DynamicTeamPromptBundleVO dynamicTeamPromptBundle() {
        return DynamicTeamPromptBundleVO.builder()
                .version(PROMPT_VERSION)
                .plannerInstruction(readRequiredPrompt("planner.md"))
                .reviewerInstruction(readRequiredPrompt("reviewer.md"))
                .summarizerInstruction(readRequiredPrompt("summarizer.md"))
                .workerInstruction(readRequiredPrompt("worker.md"))
                .build();
    }

    private String readRequiredPrompt(String fileName) {
        String location = PROMPT_DIRECTORY + fileName;
        ClassPathResource resource = new ClassPathResource(location);

        try (InputStream inputStream = resource.getInputStream()) {
            String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                throw new IllegalStateException("Dynamic team prompt is empty: " + location);
            }
            return content;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load dynamic team prompt: " + location, exception);
        }
    }

}
