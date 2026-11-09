package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 后端固定的动态团队控制角色。
 */
@Getter
@AllArgsConstructor
public enum DynamicTeamControlRoleEnum {

    PLANNER("规划智能体"),
    REVIEWER("评审智能体"),
    SUMMARIZER("汇总智能体");

    private final String description;

}
