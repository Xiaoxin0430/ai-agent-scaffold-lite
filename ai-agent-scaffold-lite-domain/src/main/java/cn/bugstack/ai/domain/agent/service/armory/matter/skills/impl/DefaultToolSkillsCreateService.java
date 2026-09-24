package cn.bugstack.ai.domain.agent.service.armory.matter.skills.impl;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.service.armory.matter.skills.ToolSkillsCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spring AI Community 构建skills <a href="https://github.com/spring-ai-community/spring-ai-agent-utils">spring-ai-agent-utils</a>
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/2/6 08:04
 */
@Slf4j
@Service
public class DefaultToolSkillsCreateService implements ToolSkillsCreateService {

    private final List<Path> extractedDirectories = new CopyOnWriteArrayList<>();

    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills) throws Exception {

        String type = toolSkills.getType();
        String path = toolSkills.getPath();

        List<ToolCallback> toolCallbackList = new ArrayList<>();

        if ("directory".equals(type)){
            ToolCallback toolCallback = SkillsTool.builder()
                    .addSkillsDirectory(path)
                    .build();
            toolCallbackList.add(toolCallback);
        }

        if ("resource".equals(type)){
            ClassPathResource resource = new ClassPathResource(path);
            // SkillsTool 目前要求真实目录；打包后的 classpath 资源位于 JAR 内。
            String skillsDirectory = resource.isFile()
                    ? resource.getFile().getAbsolutePath()
                    : extractSkillsDirectory(path);
            ToolCallback toolCallback = SkillsTool.builder()
                    .addSkillsDirectory(skillsDirectory)
                    .build();
            toolCallbackList.add(toolCallback);
        }

        return toolCallbackList.toArray(new ToolCallback[0]);
    }

    private String extractSkillsDirectory(String resourcePath) throws IOException {
        String normalizedPath = resourcePath.replace('\\', '/').replaceAll("^/+|/+$", "");
        if (normalizedPath.isBlank() || normalizedPath.contains("..")) {
            throw new IllegalArgumentException("Invalid skills resource path: " + resourcePath);
        }

        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + normalizedPath + "/**/*");
        Path directory = Files.createTempDirectory("ai-agent-skills-");
        int copiedFiles = 0;
        try {
            for (Resource resource : resources) {
                if (!resource.isReadable() || resource.getFilename() == null) {
                    continue;
                }
                String location = resource.getURL().toExternalForm();
                String marker = normalizedPath + "/";
                int markerIndex = location.lastIndexOf(marker);
                if (markerIndex < 0) {
                    continue;
                }
                String relativeName = location.substring(markerIndex + marker.length());
                if (relativeName.isBlank() || relativeName.endsWith("/")) {
                    continue;
                }
                Path target = directory.resolve(relativeName).normalize();
                if (!target.startsWith(directory)) {
                    throw new IOException("Invalid skills resource location: " + location);
                }
                Files.createDirectories(target.getParent());
                try (InputStream inputStream = resource.getInputStream()) {
                    Files.copy(inputStream, target);
                }
                copiedFiles++;
            }
            if (copiedFiles == 0) {
                throw new IOException("No skills found in classpath resource: " + normalizedPath);
            }
            log.info("Extracted {} skill resources from {}", copiedFiles, normalizedPath);
            extractedDirectories.add(directory);
            return directory.toString();
        } catch (Exception e) {
            deleteDirectory(directory);
            throw e;
        }
    }

    @PreDestroy
    public void cleanExtractedDirectories() {
        extractedDirectories.forEach(this::deleteDirectory);
        extractedDirectories.clear();
    }

    private void deleteDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("Failed to clean extracted skills resource: {}", file, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk extracted skills directory: {}", directory, e);
        }
    }

}
