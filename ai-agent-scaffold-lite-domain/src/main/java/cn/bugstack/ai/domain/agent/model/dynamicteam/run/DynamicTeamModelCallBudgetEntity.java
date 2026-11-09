package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import cn.bugstack.ai.domain.agent.service.dynamicteam.run.exception.DynamicTeamModelCallLimitException;

/** 一次运行共享的模型调用预算；额度在调用前预占且不退还。 */
public class DynamicTeamModelCallBudgetEntity {

    private final int maxModelCalls;
    private int usedModelCalls;

    public DynamicTeamModelCallBudgetEntity(int maxModelCalls) {
        if (maxModelCalls <= 0) {
            throw new IllegalArgumentException("模型调用上限必须大于 0。");
        }
        this.maxModelCalls = maxModelCalls;
    }

    public synchronized int reserveModelCall() {
        // 先检查再递增，保证并发 Worker 不能共同越过同一份运行预算。
        if (usedModelCalls >= maxModelCalls) {
            throw new DynamicTeamModelCallLimitException(
                    "模型调用次数已经达到运行上限。");
        }
        usedModelCalls++;
        return maxModelCalls - usedModelCalls;
    }

    public synchronized int getUsedModelCalls() {
        return usedModelCalls;
    }

    public synchronized int getRemainingModelCalls() {
        return maxModelCalls - usedModelCalls;
    }
}
