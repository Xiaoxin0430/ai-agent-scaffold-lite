package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** 一次运行中按 sequence 排序的轻量事实事件。 */
@Getter
@Builder
public class DynamicTeamRunEventVO {
    private final long sequence;
    private final String runId;
    private final Integer planVersion;
    private final DynamicTeamRunEventTypeEnum eventType;
    private final DynamicTeamRunStatusEnum runStatus;
    private final String workerId;
    private final String taskId;
    private final String reasonCode;
    private final String message;
    private final Instant occurredAt;
}
