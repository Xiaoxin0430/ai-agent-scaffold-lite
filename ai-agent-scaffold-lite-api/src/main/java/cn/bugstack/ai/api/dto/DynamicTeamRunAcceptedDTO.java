package cn.bugstack.ai.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DynamicTeamRunAcceptedDTO {
    private String runId;
    private String status;
}
