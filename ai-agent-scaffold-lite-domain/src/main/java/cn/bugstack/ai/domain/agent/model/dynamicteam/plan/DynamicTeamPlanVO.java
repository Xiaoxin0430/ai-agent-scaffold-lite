package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 经过后端校验和版本分配的完整计划快照。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamPlanVO {

    private String runId;
    private String schemaVersion;
    private int planVersion;
    private Integer previousPlanVersion;
    private String summary;
    private String changeReason;
    private List<DynamicTeamWorkerVO> workers;
    private List<DynamicTeamTaskVO> tasks;

}
