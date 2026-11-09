package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 后端交给 Summarizer 的单次汇总任务单。
 * 只携带汇总所需的业务字段，不暴露内部运行配置、WorkerPool 或模型连接参数。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamSummarizingCommandEntity {

    private String runId;
    private String userGoal;
    private List<String> acceptanceRequirements;
    /** 本轮正式计划中全部状态为 SUCCEEDED 的任务结果。 */
    private List<DynamicTeamTaskExecutionResultVO> succeededResults;
    private Integer remainingModelCalls;
    private Long remainingRunSeconds;

}
