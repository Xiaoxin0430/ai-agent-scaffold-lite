package cn.bugstack.ai.domain.agent.adapter.port;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;

import java.util.List;

public interface IPublishedWorkflowConfigPort {
    void insert(AiAgentConfigTableVO config);
    List<AiAgentConfigTableVO> findAll();
}
