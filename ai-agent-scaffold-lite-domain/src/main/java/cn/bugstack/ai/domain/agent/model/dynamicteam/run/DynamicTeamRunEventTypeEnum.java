package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

/** V1 运行上下文对外暴露的稳定事件类型。 */
public enum DynamicTeamRunEventTypeEnum {
    RUN_STARTED,
    RUN_STATUS_CHANGED,
    PLAN_ACCEPTED,
    TEAM_CREATED,
    TASK_STATUS_CHANGED,
    SUMMARIZING_STARTED,
    SUMMARIZING_COMPLETED,
    SUMMARIZING_FAILED,
    RUN_TERMINATED
}
