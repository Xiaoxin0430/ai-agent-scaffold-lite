package cn.bugstack.ai.domain.agent.service.dynamicteam.planning;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamControlModelPort;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamControlRoleEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamPromptBundleVO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 规划模型的使用，生成计划草稿。规划输入、控制模型调用和严格解析的领域编排。
 */
@Service
public class DynamicTeamPlannerService implements IDynamicTeamPlannerService {

    @Resource
    private DynamicTeamPlannerInputBuilder inputBuilder;
    @Resource
    private DynamicTeamPlanDraftParser planDraftParser;
    @Resource
    private IDynamicTeamControlModelPort controlModelPort;
    @Resource
    private DynamicTeamPromptBundleVO promptBundle;

    @Override
    public DynamicTeamPlanDraftVO plan(DynamicTeamPlanningCommandEntity command) {
        //组装输入
        String dynamicInput = inputBuilder.build(command);
        //调用模型
        String rawOutput = controlModelPort.generate(
                DynamicTeamControlRoleEnum.PLANNER,//角色
                promptBundle.getPlannerInstruction(),//系统提示词
                dynamicInput//动态信息
        );
        //解析模型输出
        return planDraftParser.parse(rawOutput);
    }

}
