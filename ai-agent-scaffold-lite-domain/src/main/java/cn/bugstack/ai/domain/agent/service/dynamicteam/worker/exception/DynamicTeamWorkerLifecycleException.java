package cn.bugstack.ai.domain.agent.service.dynamicteam.worker.exception;

/**
 * 临时 Worker 创建、变更或清理失败。
 */
public class DynamicTeamWorkerLifecycleException extends RuntimeException {

    public DynamicTeamWorkerLifecycleException(String message, Throwable cause) {
        super(message, cause);
    }

}
