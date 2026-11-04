package cn.bugstack.ai.domain.agent.service.armory;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.service.IWorkflowRuntimeRegistry;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SpringWorkflowRuntimeRegistry implements IWorkflowRuntimeRegistry {
    @Resource
    private ApplicationContext applicationContext;

    @Override
    public synchronized void registerIfAbsent(String workflowId, AiAgentRegisterVO registered) {
        DefaultListableBeanFactory beanFactory = beanFactory();
        if (beanFactory.containsBean(workflowId)) {
            throw new AppException(ResponseCode.CONFLICT.getCode(), "工作流 ID 已存在：" + workflowId);
        }
        BeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(AiAgentRegisterVO.class, () -> registered)
                .getBeanDefinition();
        definition.setScope(BeanDefinition.SCOPE_SINGLETON);
        beanFactory.registerBeanDefinition(workflowId, definition);
    }

    @Override
    public synchronized void unregisterIfSame(String workflowId, AiAgentRegisterVO registered) {
        DefaultListableBeanFactory beanFactory = beanFactory();
        if (registered == null || !beanFactory.containsBeanDefinition(workflowId)) {
            return;
        }
        if (applicationContext.getBean(workflowId) == registered) {
            beanFactory.removeBeanDefinition(workflowId);
        }
    }

    private DefaultListableBeanFactory beanFactory() {
        return (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    }
}
