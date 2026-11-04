package cn.bugstack.ai.domain.agent.service.armory.tree;

public interface StrategyMapper<T, D, R> {

    StrategyHandler<T, D, R> get(T request, D context) throws Exception;
}
