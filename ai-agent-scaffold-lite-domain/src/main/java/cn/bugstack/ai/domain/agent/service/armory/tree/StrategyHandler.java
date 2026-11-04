package cn.bugstack.ai.domain.agent.service.armory.tree;

public interface StrategyHandler<T, D, R> {

    StrategyHandler<?, ?, ?> DEFAULT = (request, context) -> null;

    R apply(T request, D context) throws Exception;
}
