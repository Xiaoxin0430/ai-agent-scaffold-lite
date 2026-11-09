package cn.bugstack.ai.domain.agent.service.dynamicteam.planning;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamControlModelPort;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamControlRoleEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamPromptBundleVO;
import org.springframework.stereotype.Service;

/**
 * 规划输入、控制模型调用和严格解析的领域编排。
 */
@Service
public class DynamicTeamPlannerService implements IDynamicTeamPlannerService {

    private final DynamicTeamPlannerInputBuilder inputBuilder;
    private final DynamicTeamPlanDraftParser planDraftParser;
    private final IDynamicTeamControlModelPort controlModelPort;
    private final DynamicTeamPromptBundleVO promptBundle;

    public DynamicTeamPlannerService(
            DynamicTeamPlannerInputBuilder inputBuilder,
            DynamicTeamPlanDraftParser planDraftParser,
            IDynamicTeamControlModelPort controlModelPort,
            DynamicTeamPromptBundleVO promptBundle) {
        this.inputBuilder = inputBuilder;
        this.planDraftParser = planDraftParser;
        this.controlModelPort = controlModelPort;
        this.promptBundle = promptBundle;
    }

    @Override
    public DynamicTeamPlanDraftVO plan(DynamicTeamPlanningCommandEntity command) {
        String dynamicInput = inputBuilder.build(command);

        String rawOutput = controlModelPort.generate(
                DynamicTeamControlRoleEnum.PLANNER,
                promptBundle.getPlannerInstruction(),
                dynamicInput
        );

        return planDraftParser.parse(rawOutput);
    }

}
