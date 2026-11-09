package cn.bugstack.ai.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DynamicTeamRunSnapshotDTO {
    private String runId;
    private String userGoal;
    private List<String> acceptanceRequirements;
    private String status;
    private String terminationReason;
    private Map<String, Object> currentPlan;
    private List<Map<String, Object>> currentTaskResults;
    private int usedModelCalls;
    private int remainingModelCalls;
    private Instant startedAt;
    private boolean resourcesReleased;
    private long lastEventSequence;
    private String finalAnswer;
}
