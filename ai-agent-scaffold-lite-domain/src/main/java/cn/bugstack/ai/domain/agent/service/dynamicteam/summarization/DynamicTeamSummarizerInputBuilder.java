package cn.bugstack.ai.domain.agent.service.dynamicteam.summarization;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamSummarizingCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把单次汇总任务单转换成受控的动态 JSON 输入。
 * 只传汇总所需的业务字段：用户目标、验收要求、成功任务结果和剩余预算。
 * 不向模型暴露 API Key、运行上限、WorkerPool 内部状态或原始异常。
 */
@Component
public class DynamicTeamSummarizerInputBuilder {

    private final ObjectMapper objectMapper;

    public DynamicTeamSummarizerInputBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(DynamicTeamSummarizingCommandEntity command) {
        validate(command);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userGoal", command.getUserGoal());
        input.put("acceptanceRequirements", safeList(command.getAcceptanceRequirements()));
        input.put("remainingModelCalls", command.getRemainingModelCalls());
        input.put("remainingRunSeconds", command.getRemainingRunSeconds());
        // 只传 SUCCEEDED 任务的 workerId / taskId / title / output，不传原始状态枚举和失败信息
        input.put("workerResults", buildWorkerResults(command.getSucceededResults()));

        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法构造 Summarizer 动态输入。", exception);
        }
    }

    private List<Map<String, String>> buildWorkerResults(
            List<DynamicTeamTaskExecutionResultVO> results) {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .map(r -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("workerId", r.getWorkerId());
                    entry.put("taskId", r.getTaskId());
                    entry.put("output", r.getOutput() != null ? r.getOutput() : "");
                    return entry;
                })
                .toList();
    }

    private void validate(DynamicTeamSummarizingCommandEntity command) {
        if (command == null) {
            throw new IllegalArgumentException("汇总命令不能为空。");
        }
        if (command.getRunId() == null || command.getRunId().isBlank()) {
            throw new IllegalArgumentException("runId 不能为空。");
        }
        if (command.getUserGoal() == null || command.getUserGoal().isBlank()) {
            throw new IllegalArgumentException("用户目标不能为空。");
        }
        if (command.getSucceededResults() == null || command.getSucceededResults().isEmpty()) {
            throw new IllegalArgumentException("汇总命令必须包含至少一个成功任务结果。");
        }
        if (command.getRemainingModelCalls() == null || command.getRemainingModelCalls() < 0) {
            throw new IllegalArgumentException("剩余模型调用次数不能为负数。");
        }
        if (command.getRemainingRunSeconds() == null || command.getRemainingRunSeconds() < 0) {
            throw new IllegalArgumentException("剩余运行时间不能为负数。");
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

}
