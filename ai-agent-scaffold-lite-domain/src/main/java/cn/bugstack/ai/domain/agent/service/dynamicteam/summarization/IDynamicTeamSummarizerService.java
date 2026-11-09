package cn.bugstack.ai.domain.agent.service.dynamicteam.summarization;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamSummarizingCommandEntity;

/**
 * 汇总智能体领域服务接口。
 * 只在正式计划全部任务成功后调用，不用于异常终态的结果组装。
 */
public interface IDynamicTeamSummarizerService {

    /**
     * 根据全部成功的任务结果生成最终汇总回答。
     *
     * @param command 汇总任务单（用户目标、验收要求、成功任务结果、剩余预算）
     * @return 非空的汇总文本
     * @throws IllegalArgumentException 命令或必填字段为空时
     * @throws IllegalStateException    模型返回空文本时
     * @throws RuntimeException         模型调用失败时
     */
    String summarize(DynamicTeamSummarizingCommandEntity command);

}
