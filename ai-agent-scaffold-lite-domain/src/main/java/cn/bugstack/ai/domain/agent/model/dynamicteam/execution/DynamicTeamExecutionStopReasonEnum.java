package cn.bugstack.ai.domain.agent.model.dynamicteam.execution;

/** 一轮任务调度停止的原因，由运行协调服务映射为整次运行状态。 */
public enum DynamicTeamExecutionStopReasonEnum {
    ROUND_COMPLETED,
    USER_CANCELLED,
    MODEL_CALL_TIMEOUT,
    RUN_TIMEOUT,
    MODEL_CALL_LIMIT_REACHED,
    INTERRUPTED
}
