package cn.bugstack.ai.domain.agent.service.dynamicteam.worker;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamWorkerAgentFactory;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerPoolEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamModelCallBudgetEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;
import cn.bugstack.ai.domain.agent.service.dynamicteam.worker.exception.DynamicTeamWorkerLifecycleException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 临时 Worker 的创建、变更与最终清理。
 */
@Service
public class DynamicTeamWorkerLifecycleService
        implements IDynamicTeamWorkerLifecycleService {

    @Resource
    private IDynamicTeamWorkerAgentFactory workerAgentFactory;

    @Resource
    private DynamicTeamRuntimePolicyVO runtimePolicy;

    @Override
    public DynamicTeamWorkerPoolEntity createTeam(
            DynamicTeamPlanVO plan,
            DynamicTeamModelCallBudgetEntity modelCallBudget) {
        validateInitialPlan(plan);
        requireModelCallBudget(modelCallBudget);
        //建空池
        DynamicTeamWorkerPoolEntity pool = new DynamicTeamWorkerPoolEntity(
                plan.getRunId(), plan.getPlanVersion());
        String currentWorkerId = null;
        //遍历计划中的Worker，创建并添加到池中
        try {
            for (DynamicTeamWorkerVO worker : plan.getWorkers()) {
                currentWorkerId = worker.getWorkerId();
                DynamicTeamWorkerRuntimeEntity runtime = workerAgentFactory.create(
                        plan.getRunId(), plan.getPlanVersion(), worker,
                        modelCallBudget);
                addCreatedRuntime(pool, runtime);
            }
            return pool;
        } catch (RuntimeException exception) {
            closeWithSuppressed(pool, exception);
            throw lifecycleFailure(
                    "临时团队创建失败，runId=" + plan.getRunId()
                            + "，workerId=" + currentWorkerId,
                    exception);
        }
    }

    @Override
    public void addWorker(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion,
            DynamicTeamWorkerVO worker,
            DynamicTeamModelCallBudgetEntity modelCallBudget) {
        requirePool(pool);
        requireModelCallBudget(modelCallBudget);
        String workerId = worker == null ? null : worker.getWorkerId();

        try {
            synchronized (pool) {
                validateMutation(pool, targetPlanVersion, worker);
                if (pool.contains(workerId)) {
                    throw new IllegalStateException(
                            "待新增 Worker 已存在，workerId=" + workerId);
                }
                if (pool.size() >= runtimePolicy.getMaxWorkers()) {
                    throw new IllegalStateException("Worker 数量已达到运行上限。");
                }

                DynamicTeamWorkerRuntimeEntity runtime = workerAgentFactory.create(
                        pool.getRunId(), targetPlanVersion, worker,
                        modelCallBudget);
                addCreatedRuntime(pool, runtime);
            }
        } catch (RuntimeException exception) {
            closeWithSuppressed(pool, exception);
            throw lifecycleFailure(
                    "新增临时 Worker 失败，runId=" + pool.getRunId()
                            + "，workerId=" + workerId,
                    exception);
        }
    }

    @Override
    public void replaceWorker(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion,
            DynamicTeamWorkerVO worker,
            DynamicTeamModelCallBudgetEntity modelCallBudget) {
        requirePool(pool);
        requireModelCallBudget(modelCallBudget);
        String workerId = worker == null ? null : worker.getWorkerId();

        try {
            synchronized (pool) {
                validateMutation(pool, targetPlanVersion, worker);
                if (!pool.contains(workerId)) {
                    throw new IllegalStateException(
                            "待替换 Worker 不存在，workerId=" + workerId);
                }

                // 先释放旧实例，避免计划版本切换期间突破 Worker 存活数量上限。
                pool.removeAndClose(workerId);
                DynamicTeamWorkerRuntimeEntity runtime = workerAgentFactory.create(
                        pool.getRunId(), targetPlanVersion, worker,
                        modelCallBudget);
                addCreatedRuntime(pool, runtime);
            }
        } catch (RuntimeException exception) {
            closeWithSuppressed(pool, exception);
            throw lifecycleFailure(
                    "替换临时 Worker 失败，runId=" + pool.getRunId()
                            + "，workerId=" + workerId,
                    exception);
        }
    }

    @Override
    public void removeWorker(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion,
            String workerId) {
        requirePool(pool);

        try {
            synchronized (pool) {
                validateTargetPlanVersion(pool, targetPlanVersion);
                if (workerId == null || workerId.isBlank()) {
                    throw new IllegalArgumentException("workerId 不能为空。");
                }
                pool.removeAndClose(workerId);
            }
        } catch (RuntimeException exception) {
            closeWithSuppressed(pool, exception);
            throw lifecycleFailure(
                    "移除临时 Worker 失败，runId=" + pool.getRunId()
                            + "，workerId=" + workerId,
                    exception);
        }
    }

    @Override
    public void commitPlanVersion(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion) {
        requirePool(pool);
        try {
            synchronized (pool) {
                validateTargetPlanVersion(pool, targetPlanVersion);
                pool.commitPlanVersion(targetPlanVersion);
            }
        } catch (RuntimeException exception) {
            closeWithSuppressed(pool, exception);
            throw lifecycleFailure(
                    "提交 WorkerPool 计划版本失败，runId=" + pool.getRunId(),
                    exception);
        }
    }

    @Override
    public void cleanup(DynamicTeamWorkerPoolEntity pool) {
        if (pool == null) {
            return;
        }
        try {
            pool.close();
        } catch (RuntimeException exception) {
            throw lifecycleFailure(
                    "清理 WorkerPool 失败，runId=" + pool.getRunId(),
                    exception);
        }
    }

    private void validateInitialPlan(DynamicTeamPlanVO plan) {
        if (plan == null) {
            throw new IllegalArgumentException("团队计划不能为空。");
        }
        if (plan.getRunId() == null || plan.getRunId().isBlank()) {
            throw new IllegalArgumentException("团队计划 runId 不能为空。");
        }
        if (plan.getPlanVersion() <= 0) {
            throw new IllegalArgumentException("团队计划版本必须大于 0。");
        }

        List<DynamicTeamWorkerVO> workers = plan.getWorkers();
        if (workers == null || workers.isEmpty()) {
            throw new IllegalArgumentException("团队至少需要一个 Worker。");
        }
        if (workers.size() > runtimePolicy.getMaxWorkers()) {
            throw new IllegalArgumentException("Worker 数量超过运行上限。");
        }
    }

    private void validateMutation(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion,
            DynamicTeamWorkerVO worker) {
        validateTargetPlanVersion(pool, targetPlanVersion);
        if (worker == null
                || worker.getWorkerId() == null
                || worker.getWorkerId().isBlank()) {
            throw new IllegalArgumentException("Worker 定义和 workerId 不能为空。");
        }
    }

    private void validateTargetPlanVersion(
            DynamicTeamWorkerPoolEntity pool,
            int targetPlanVersion) {
        if (pool.isClosed()) {
            throw new IllegalStateException("WorkerPool 已关闭，runId=" + pool.getRunId());
        }
        if (targetPlanVersion != pool.getCurrentPlanVersion() + 1) {
            throw new IllegalStateException(
                    "目标计划版本必须是当前版本加一，current="
                            + pool.getCurrentPlanVersion()
                            + "，target=" + targetPlanVersion);
        }
    }

    private void requirePool(DynamicTeamWorkerPoolEntity pool) {
        if (pool == null) {
            throw new IllegalArgumentException("WorkerPool 不能为空。");
        }
    }

    private void requireModelCallBudget(
            DynamicTeamModelCallBudgetEntity modelCallBudget) {
        if (modelCallBudget == null) {
            throw new IllegalArgumentException("模型调用预算不能为空。");
        }
    }

    private void addCreatedRuntime(
            DynamicTeamWorkerPoolEntity pool,
            DynamicTeamWorkerRuntimeEntity runtime) {
        try {
            pool.add(runtime);
        } catch (RuntimeException exception) {
            runtime.close();
            throw exception;
        }
    }

    private void closeWithSuppressed(
            DynamicTeamWorkerPoolEntity pool,
            RuntimeException primaryException) {
        try {
            pool.close();
        } catch (RuntimeException cleanupException) {
            primaryException.addSuppressed(cleanupException);
        }
    }

    private DynamicTeamWorkerLifecycleException lifecycleFailure(
            String message,
            RuntimeException cause) {
        return new DynamicTeamWorkerLifecycleException(message, cause);
    }

}
