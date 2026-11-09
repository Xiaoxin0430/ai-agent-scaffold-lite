package cn.bugstack.ai.domain.agent.service.dynamicteam.run;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunContextAggregate;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStartCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunSnapshotVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;

/** 面向 HTTP 入口的异步运行用例。 */
public interface IDynamicTeamRunService {

    DynamicTeamRunContextAggregate start(DynamicTeamRunStartCommandEntity command);

    DynamicTeamRunSnapshotVO query(String runId);

    DynamicTeamRunSnapshotVO cancel(String runId);

    DynamicTeamRunEventSubscription subscribe(String runId, long afterSequence);

    DynamicTeamRuntimePolicyVO capabilities();
}
