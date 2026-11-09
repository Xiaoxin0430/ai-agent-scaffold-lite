package cn.bugstack.ai.domain.agent.model.dynamicteam.execution;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamTaskVO;
import lombok.Getter;

/** 只由当前轮协调线程访问；不暴露状态 setter。 */
@Getter
public class DynamicTeamTaskRuntimeEntity {
    /** 模块 4 已接受的任务定义，本轮执行期间保持不变。 */
    private final DynamicTeamTaskVO task;
    /** 尚未成功的直接上游数量；减到 0 时任务从 WAITING 进入 READY。 */
    private int remainingDependencies;
    private DynamicTeamTaskStatusEnum status;
    private long startedNanos;
    private DynamicTeamTaskExecutionResultVO result;

    public DynamicTeamTaskRuntimeEntity(DynamicTeamTaskVO task) {
        this.task = task;
        remainingDependencies = task.getDependsOn().size();
        status = remainingDependencies == 0 ? DynamicTeamTaskStatusEnum.READY : DynamicTeamTaskStatusEnum.WAITING;
    }

    public void dependencySucceeded() {
        // 只有协调线程会调用该方法，执行线程不能直接修改依赖计数。
        if (status != DynamicTeamTaskStatusEnum.WAITING) return;
        if (remainingDependencies <= 0) throw new IllegalStateException("依赖计数异常。");
        remainingDependencies--;
        if (remainingDependencies == 0) status = DynamicTeamTaskStatusEnum.READY;
    }

    public void start() {
        if (status != DynamicTeamTaskStatusEnum.READY) throw new IllegalStateException("任务尚未就绪。");
        status = DynamicTeamTaskStatusEnum.RUNNING;
        startedNanos = System.nanoTime();
    }

    public void finish(DynamicTeamTaskStatusEnum terminalStatus, String output, String failure) {
        // 第一个终态结果生效，取消后迟到的模型结果不能让任务“复活”。
        if (result != null) return;
        if (terminalStatus == DynamicTeamTaskStatusEnum.SUCCEEDED && status != DynamicTeamTaskStatusEnum.RUNNING) {
            throw new IllegalStateException("未执行的任务不能成功。");
        }
        if (terminalStatus == DynamicTeamTaskStatusEnum.WAITING || terminalStatus == DynamicTeamTaskStatusEnum.READY
                || terminalStatus == DynamicTeamTaskStatusEnum.RUNNING) throw new IllegalArgumentException("需要任务终态。");
        status = terminalStatus;
        result = DynamicTeamTaskExecutionResultVO.builder().taskId(task.getTaskId())
                .workerId(task.getWorkerId()).status(status).output(output).failureMessage(failure).build();
    }
}
