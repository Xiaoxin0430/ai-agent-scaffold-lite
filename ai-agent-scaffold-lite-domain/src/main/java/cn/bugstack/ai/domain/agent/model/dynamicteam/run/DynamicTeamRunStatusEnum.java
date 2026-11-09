package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

/** 一次动态团队运行的阶段和终态。 */
public enum DynamicTeamRunStatusEnum {
    PLANNING,
    VALIDATING,
    CREATING,
    EXECUTING,
    REVIEWING,
    REPLANNING,
    SUMMARIZING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    CANCELLED,
    QUALITY_REJECTED;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == FAILED
                || this == TIMEOUT
                || this == CANCELLED
                || this == QUALITY_REJECTED;
    }
}
