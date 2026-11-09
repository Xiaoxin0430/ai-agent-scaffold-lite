package cn.bugstack.ai.domain.agent.model.dynamicteam.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 启动动态团队运行时允许调用方提供的业务输入。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamRunStartCommandEntity {
    private String userGoal;
    private List<String> acceptanceRequirements;
}
