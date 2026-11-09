package cn.bugstack.ai.domain.agent.service.dynamicteam.summarization;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamControlModelPort;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamSummarizingCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamControlRoleEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamPromptBundleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 汇总智能体领域服务实现。
 * 结构与 DynamicTeamPlannerService 对称：输入 Builder → 控制模型调用 → 返回文本。
 * 不解析 JSON，不创建 Agent，不修改运行上下文；文本写入由 Coordinator 完成。
 */
@Service
@Slf4j
public class DynamicTeamSummarizerService implements IDynamicTeamSummarizerService {

    private final DynamicTeamSummarizerInputBuilder inputBuilder;
    private final IDynamicTeamControlModelPort controlModelPort;
    private final DynamicTeamPromptBundleVO promptBundle;

    public DynamicTeamSummarizerService(
            DynamicTeamSummarizerInputBuilder inputBuilder,
            IDynamicTeamControlModelPort controlModelPort,
            DynamicTeamPromptBundleVO promptBundle) {
        this.inputBuilder = inputBuilder;
        this.controlModelPort = controlModelPort;
        this.promptBundle = promptBundle;
    }

    @Override
    public String summarize(DynamicTeamSummarizingCommandEntity command) {
        String dynamicInput = inputBuilder.build(command);

        log.debug("SUMMARIZER 调用开始 runId={}, resultCount={}",
                command.getRunId(),
                command.getSucceededResults() != null ? command.getSucceededResults().size() : 0);

        String rawOutput = controlModelPort.generate(
                DynamicTeamControlRoleEnum.SUMMARIZER,
                promptBundle.getSummarizerInstruction(),
                dynamicInput
        );

        // 空文本或仅空白表示汇总失败，不能把空结果包装为成功回答
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new IllegalStateException("汇总智能体返回了空结果，runId=" + command.getRunId());
        }

        log.debug("SUMMARIZER 调用完成 runId={}, outputLength={}", command.getRunId(), rawOutput.length());
        return rawOutput.strip();
    }

}
