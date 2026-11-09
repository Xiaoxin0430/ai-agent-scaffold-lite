package cn.bugstack.ai.domain.agent.service.dynamicteam.run.exception;

/** 模型调用预算已经耗尽，调用方必须停止本次运行。 */
public class DynamicTeamModelCallLimitException extends IllegalStateException {
    public DynamicTeamModelCallLimitException(String message) {
        super(message);
    }
}
