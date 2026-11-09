package cn.bugstack.ai.domain.agent.service.dynamicteam.run;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunContextAggregate;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStartCommandEntity;

/** 动态团队运行级编排入口。 */
public interface IDynamicTeamRunCoordinatorService {

    DynamicTeamRunContextAggregate createRun(
            DynamicTeamRunStartCommandEntity command);

    void executeInitialRound(DynamicTeamRunContextAggregate context);

    void cancel(DynamicTeamRunContextAggregate context);
}
