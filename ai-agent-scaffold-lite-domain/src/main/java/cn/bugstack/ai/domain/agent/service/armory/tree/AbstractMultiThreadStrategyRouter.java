package cn.bugstack.ai.domain.agent.service.armory.tree;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class AbstractMultiThreadStrategyRouter<T, D, R>
        implements StrategyMapper<T, D, R>, StrategyHandler<T, D, R> {

    @SuppressWarnings("unchecked")
    protected StrategyHandler<T, D, R> defaultStrategyHandler =
            (StrategyHandler<T, D, R>) StrategyHandler.DEFAULT;

    public R router(T request, D context) throws Exception {
        StrategyHandler<T, D, R> handler = get(request, context);
        if (handler != null) {
            return handler.apply(request, context);
        }
        return defaultStrategyHandler.apply(request, context);
    }

    @Override
    public R apply(T request, D context) throws Exception {
        multiThread(request, context);
        return doApply(request, context);
    }

    protected abstract void multiThread(T request, D context)
            throws ExecutionException, InterruptedException, TimeoutException;

    protected abstract R doApply(T request, D context) throws Exception;
}
