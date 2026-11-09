package cn.bugstack.ai.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DynamicTeamCapabilitiesDTO {
    private boolean enabled;
    private int maxWorkers;
    private int maxTasks;
    private int maxConcurrentWorkers;
    private int stepTimeoutSeconds;
    private int runTimeoutSeconds;
    private int maxModelCalls;
    private int maxUserInputCharacters;
}
