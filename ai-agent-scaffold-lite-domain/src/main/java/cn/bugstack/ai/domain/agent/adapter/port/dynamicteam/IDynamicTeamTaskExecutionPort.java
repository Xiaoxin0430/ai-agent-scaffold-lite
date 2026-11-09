package cn.bugstack.ai.domain.agent.adapter.port.dynamicteam;

import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamTaskVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import java.util.Map;

/** 一次无工具 Worker 调用；调度层处理期限和取消。 */
public interface IDynamicTeamTaskExecutionPort {
    String execute(String userGoal, DynamicTeamTaskVO task, DynamicTeamWorkerRuntimeEntity worker,
                   Map<String, DynamicTeamTaskExecutionResultVO> dependencyResults) throws Exception;
}
