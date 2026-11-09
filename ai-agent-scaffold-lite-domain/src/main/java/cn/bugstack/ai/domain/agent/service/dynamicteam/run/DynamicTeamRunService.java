package cn.bugstack.ai.domain.agent.service.dynamicteam.run;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunContextAggregate;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStartCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStatusEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunTerminationReasonEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunSnapshotVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 有界异步执行和进程内运行注册表。V1 不承诺进程重启恢复。 */
@Service
public class DynamicTeamRunService implements IDynamicTeamRunService {

    private static final int MAX_RETAINED_RUNS = 100;
    //保留时间
    private static final long RETENTION_MILLIS = TimeUnit.MINUTES.toMillis(30);

    @Resource
    private IDynamicTeamRunCoordinatorService coordinatorService;
    @Resource
    private DynamicTeamRuntimePolicyVO runtimePolicy;
    @Resource
    private DynamicTeamRunEventHub eventHub;

    //运行注册表
    private final Map<String, ManagedRun> runs = new ConcurrentHashMap<>();
    //JAVA锁，保护单实例多进程
    private final Object registryLock = new Object();

    private final ThreadPoolExecutor runExecutor = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20),
            new ThreadPoolExecutor.AbortPolicy());

    @Override
    public DynamicTeamRunContextAggregate start(DynamicTeamRunStartCommandEntity command) {
        //校验前端请求
        validateStartCommand(command);
        //创建运行上下文聚会对象
        DynamicTeamRunContextAggregate context = coordinatorService.createRun(command);
        //一个运行注册表记录
        ManagedRun managedRun = new ManagedRun(context);

        synchronized (registryLock) {
            purgeExpiredRuns();
            if (runs.size() >= MAX_RETAINED_RUNS) {
                throw new IllegalStateException("运行注册表已满，请稍后重试。");
            }
            if (runs.putIfAbsent(context.getRunId(), managedRun) != null) {
                throw new IllegalStateException("运行编号冲突，请重试。");
            }
        }
        try {
            //提交给线程池，返回一个future
            Future<?> future = runExecutor.submit(() -> {
                if (!managedRun.markStarted()) {
                    return;
                }
                try {
                    coordinatorService.executeInitialRound(context);
                } finally {
                    managedRun.markFinished();
                }
            });
            managedRun.attachFuture(future);
            return context;
        } catch (RuntimeException exception) {
            runs.remove(context.getRunId(), managedRun);
            context.terminate(DynamicTeamRunStatusEnum.FAILED,
                    DynamicTeamRunTerminationReasonEnum.INTERNAL_ERROR, Instant.now());
            context.markResourcesReleased();
            throw new IllegalStateException("运行服务繁忙，请稍后重试。", exception);
        }
    }

    @Override
    public DynamicTeamRunSnapshotVO query(String runId) {

        return requiredRun(runId).context.snapshot();
    }

    @Override
    public DynamicTeamRunSnapshotVO cancel(String runId) {
        ManagedRun managedRun = requiredRun(runId);
        if (managedRun.cancelBeforeStart()) {
            managedRun.context.requestCancellation();
            managedRun.context.terminate(DynamicTeamRunStatusEnum.CANCELLED,
                    DynamicTeamRunTerminationReasonEnum.USER_CANCELLED, Instant.now());
            managedRun.context.markResourcesReleased();
            managedRun.markFinished();
        } else {
            coordinatorService.cancel(managedRun.context);
        }
        return managedRun.context.snapshot();
    }

    @Override
    public DynamicTeamRunEventSubscription subscribe(String runId, long afterSequence) {
        return eventHub.subscribe(requiredRun(runId).context, afterSequence);
    }

    @Override
    public DynamicTeamRuntimePolicyVO capabilities() {
        return runtimePolicy;
    }

    @PreDestroy
    public void shutdown() {
        runExecutor.shutdownNow();
    }

    //校验
    private void validateStartCommand(
            DynamicTeamRunStartCommandEntity command) {
        if (!runtimePolicy.isEnabled()) {
            throw new IllegalStateException("动态团队运行能力未启用。");
        }
        if (command == null
                || command.getUserGoal() == null
                || command.getUserGoal().isBlank()) {
            throw new IllegalArgumentException("用户目标不能为空。");
        }
        if (command.getUserGoal().length()
                > runtimePolicy.getMaxUserInputCharacters()) {
            throw new IllegalArgumentException("用户目标长度超过运行上限。");
        }
        if (command.getAcceptanceRequirements() == null) {
            throw new IllegalArgumentException("验收要求不能为空。");
        }
    }

    private ManagedRun requiredRun(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空。");
        }
        ManagedRun managedRun = runs.get(runId);
        if (managedRun == null) {
            throw new IllegalArgumentException("运行不存在或已过期。");
        }
        return managedRun;
    }

    //清除已过期的运行记录
    private void purgeExpiredRuns() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ManagedRun> entry : runs.entrySet()) {
            ManagedRun run = entry.getValue();
            if (run.finishedAtMillis > 0L && now - run.finishedAtMillis >= RETENTION_MILLIS) {
                runs.remove(entry.getKey(), run);
            }
        }
    }

    private static final class ManagedRun {
        private final DynamicTeamRunContextAggregate context;
        private Future<?> future;
        private boolean started;
        private boolean cancellationRequested;
        private volatile long finishedAtMillis;//运行结束时间

        private ManagedRun(DynamicTeamRunContextAggregate context) {
            this.context = context;
        }

        private synchronized void attachFuture(Future<?> future) {
            this.future = future;
            if (cancellationRequested) {
                future.cancel(true);
            }
        }

        private synchronized boolean markStarted() {
            if (cancellationRequested) {
                return false;
            }
            started = true;
            return true;
        }

        private synchronized boolean cancelBeforeStart() {
            cancellationRequested = true;
            if (future != null) {
                future.cancel(true);
            }
            return !started;
        }

        private void markFinished() {
            finishedAtMillis = System.currentTimeMillis();
        }
    }
}
