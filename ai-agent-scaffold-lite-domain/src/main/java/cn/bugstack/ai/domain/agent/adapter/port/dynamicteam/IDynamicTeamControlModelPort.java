package cn.bugstack.ai.domain.agent.adapter.port.dynamicteam;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamControlRoleEnum;

/**
 * 后端固定控制模型调用端口。
 */
public interface IDynamicTeamControlModelPort {

    String generate(
            DynamicTeamControlRoleEnum controlRole,
            String systemInstruction,
            String dynamicInput
    );

}
