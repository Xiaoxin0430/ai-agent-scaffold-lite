package cn.bugstack.ai.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DynamicTeamRunEventDTO {
    private long sequence;
    private String runId;
    private Integer planVersion;
    private String eventType;
    private String runStatus;
    private String workerId;
    private String taskId;
    private String reasonCode;
    private String message;
    private Instant occurredAt;
}
