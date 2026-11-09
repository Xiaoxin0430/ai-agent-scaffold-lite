package cn.bugstack.ai.domain.agent.model.dynamicteam.execution;

/** 单个计划版本内的任务状态。 */
public enum DynamicTeamTaskStatusEnum {
    WAITING, READY, RUNNING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, BLOCKED
}
