package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;

public interface IWorkflowRuntimeRegistry {
    void registerIfAbsent(String workflowId, AiAgentRegisterVO registered);
}
