package cn.bugstack.ai.domain.agent.service.dynamicteam.worker;

import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerPoolEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamModelCallBudgetEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;

/**
 * 动态团队临时 Worker 生命周期服务。
 */
public interface IDynamicTeamWorkerLifecycleService {

    DynamicTeamWorkerPoolEntity createTeam(
            DynamicTeamPlanVO plan,
            DynamicTeamModelCallBudgetEntity modelCallBudget);

    void addWorker(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion,
            DynamicTeamWorkerVO worker,
            DynamicTeamModelCallBudgetEntity modelCallBudget);

    void replaceWorker(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion,
            DynamicTeamWorkerVO worker,
            DynamicTeamModelCallBudgetEntity modelCallBudget);

    void removeWorker(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion,
            String workerId);

    void commitPlanVersion(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion);

    void cleanup(DynamicTeamWorkerPoolEntity pool);

}
