package cn.bugstack.ai.domain.agent.service.canvas;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.adapter.port.IPublishedWorkflowConfigPort;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowCanvasVO;
import cn.bugstack.ai.domain.agent.model.valobj.canvas.WorkflowPublishResultVO;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import cn.bugstack.ai.domain.agent.service.IWorkflowCanvasService;
import cn.bugstack.ai.domain.agent.service.IWorkflowPublishService;
import cn.bugstack.ai.domain.agent.service.IWorkflowRuntimeRegistry;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.List;

@Service
public class WorkflowPublishService implements IWorkflowPublishService {

    @Resource
    private IWorkflowCanvasService canvasService;

    @Resource
    private IArmoryService armoryService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private WorkflowRuntimeConfigFactory runtimeConfigFactory;

    @Resource
    private IPublishedWorkflowConfigPort publishedWorkflowConfigPort;

    @Resource
    private IWorkflowRuntimeRegistry workflowRuntimeRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized WorkflowPublishResultVO publish(WorkflowCanvasVO canvas) throws Exception {
        var conversion = canvasService.validateAndConvert(canvas);
        if (!conversion.valid()) {
            return new WorkflowPublishResultVO(conversion.validation(), null);
        }

        AiAgentConfigTableVO config = conversion.config();
        String workflowId = config.getWorkflow().getId();
        if (applicationContext.containsBean(workflowId)) {
            throw new AppException(ResponseCode.CONFLICT.getCode(), "工作流 ID 已发布，请使用新的 ID。");
        }

        AiAgentConfigTableVO runtimeConfig = runtimeConfigFactory.create(config);
        try {
            publishedWorkflowConfigPort.insert(config);
        } catch (DuplicateKeyException e) {
            throw new AppException(ResponseCode.CONFLICT.getCode(), "工作流 ID 已发布，请使用新的 ID。", e);
        }

        AiAgentRegisterVO registered = null;
        try {
            armoryService.acceptArmoryAgents(List.of(runtimeConfig));
            registered = applicationContext.getBean(workflowId, AiAgentRegisterVO.class);
            if (!workflowId.equals(registered.getWorkflowId()) || registered.getRunner() == null) {
                throw new IllegalStateException("工作流装配后未正确注册。");
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                AiAgentRegisterVO thisRegistration = registered;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            workflowRuntimeRegistry.unregisterIfSame(workflowId, thisRegistration);
                        }
                    }
                });
            }
        } catch (Exception e) {
            workflowRuntimeRegistry.unregisterIfSame(workflowId, registered);
            throw e;
        }
        return new WorkflowPublishResultVO(conversion.validation(), workflowId);
    }
}
