package cn.bugstack.ai.domain.agent.service.dynamicteam.planning;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningModeEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把单次规划任务单转换成受控的动态 JSON 输入。
 */
@Component
public class DynamicTeamPlannerInputBuilder {

    private final ObjectMapper objectMapper;

    public DynamicTeamPlannerInputBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(DynamicTeamPlanningCommandEntity command) {
        validate(command);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("mode", command.getMode().name());
        input.put("userGoal", command.getUserGoal());
        input.put("acceptanceRequirements", safeList(command.getAcceptanceRequirements()));
        input.put("runtimePolicy", buildRuntimePolicy(command.getRuntimePolicy()));
        input.put("remainingModelCalls", command.getRemainingModelCalls());
        input.put("remainingRunSeconds", command.getRemainingRunSeconds());

        if (DynamicTeamPlanningModeEnum.REPLAN.equals(command.getMode())) {
            input.put("previousPlan", command.getPreviousPlan());
            input.put("completedTasks", safeList(command.getCompletedTasks()));
            input.put("reviewIssues", safeList(command.getReviewIssues()));
        }

        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法构造 Planner 动态输入。", exception);
        }
    }

    private Map<String, Object> buildRuntimePolicy(DynamicTeamRuntimePolicyVO policy) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("maxWorkers", policy.getMaxWorkers());
        values.put("maxTasks", policy.getMaxTasks());
        values.put("maxConcurrentWorkers", policy.getMaxConcurrentWorkers());
        values.put("maxReplans", policy.getMaxReplans());
        values.put("stepTimeoutSeconds", policy.getStepTimeoutSeconds());
        values.put("runTimeoutSeconds", policy.getRunTimeoutSeconds());
        values.put("maxModelCalls", policy.getMaxModelCalls());
        values.put("maxUserInputCharacters", policy.getMaxUserInputCharacters());
        return values;
    }

    private void validate(DynamicTeamPlanningCommandEntity command) {
        if (command == null) {
            throw new IllegalArgumentException("规划命令不能为空。");
        }
        if (blank(command.getRunId())) {
            throw new IllegalArgumentException("runId 不能为空。");
        }
        if (command.getMode() == null) {
            throw new IllegalArgumentException("规划模式不能为空。");
        }
        if (blank(command.getUserGoal())) {
            throw new IllegalArgumentException("用户目标不能为空。");
        }
        if (command.getRuntimePolicy() == null) {
            throw new IllegalArgumentException("动态团队运行规则不能为空。");
        }
        validateUserInputLength(command);
        if (command.getRemainingModelCalls() == null || command.getRemainingModelCalls() < 0) {
            throw new IllegalArgumentException("剩余模型调用次数不能为负数。");
        }
        if (command.getRemainingRunSeconds() == null || command.getRemainingRunSeconds() < 0) {
            throw new IllegalArgumentException("剩余运行时间不能为负数。");
        }

        if (DynamicTeamPlanningModeEnum.INITIAL.equals(command.getMode())) {
            if (command.getPreviousPlan() != null) {
                throw new IllegalArgumentException("初始规划不能携带上一版计划。");
            }
            return;
        }

        if (command.getPreviousPlan() == null) {
            throw new IllegalArgumentException("重新规划必须携带上一版计划。");
        }
        if (!command.getRunId().equals(command.getPreviousPlan().getRunId())) {
            throw new IllegalArgumentException("上一版计划不属于当前 runId。");
        }
    }

    private void validateUserInputLength(DynamicTeamPlanningCommandEntity command) {
        int totalCharacters = command.getUserGoal().length();
        List<String> requirements = command.getAcceptanceRequirements();

        if (requirements != null) {
            for (int index = 0; index < requirements.size(); index++) {
                String requirement = requirements.get(index);
                if (blank(requirement)) {
                    throw new IllegalArgumentException(
                            "验收要求不能为空，index=" + index);
                }
                totalCharacters += requirement.length();
                if (totalCharacters > command.getRuntimePolicy().getMaxUserInputCharacters()) {
                    throw new IllegalArgumentException(
                            "用户目标和验收要求的总长度超过允许上限。");
                }
            }
        }

        if (totalCharacters > command.getRuntimePolicy().getMaxUserInputCharacters()) {
            throw new IllegalArgumentException(
                    "用户目标和验收要求的总长度超过允许上限。");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

}
