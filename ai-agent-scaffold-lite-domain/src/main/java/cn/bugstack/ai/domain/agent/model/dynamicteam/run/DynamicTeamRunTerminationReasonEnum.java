package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

/** 动态团队运行进入终态的业务原因。 */
public enum DynamicTeamRunTerminationReasonEnum {
    NORMAL_COMPLETION,
    USER_CANCELLED,
    RUN_TIMEOUT,
    MODEL_CALL_TIMEOUT,
    MODEL_CALL_LIMIT_REACHED,
    PLANNING_FAILED,
    PLAN_VALIDATION_FAILED,
    TEAM_CREATION_FAILED,
    TASK_EXECUTION_FAILED,
    SUMMARIZING_FAILED,
    QUALITY_REPLAN_LIMIT_REACHED,
    INTERNAL_ERROR
}
