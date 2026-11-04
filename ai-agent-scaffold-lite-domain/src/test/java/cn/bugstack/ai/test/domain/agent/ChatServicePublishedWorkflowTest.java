package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.bugstack.ai.domain.agent.service.chat.ChatService;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Single;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChatServicePublishedWorkflowTest {
    @Test
    public void list_includes_runtime_registered_workflows() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            context.getBeanFactory().registerSingleton("wf_new", AiAgentRegisterVO.builder()
                    .workflowId("wf_new").workflowName("新工作流").workflowDescription("画布发布").build());
            ChatService chatService = new ChatService();
            ReflectionTestUtils.setField(chatService, "applicationContext", context);

            var workflows = chatService.queryAiAgentConfigList();

            Assert.assertEquals(List.of("wf_new"), workflows.stream().map(item -> item.getId()).toList());
            Assert.assertEquals("新工作流", workflows.get(0).getName());
        }
    }

    @Test
    public void same_user_gets_separate_sessions_for_different_workflows() {
        DefaultArmoryFactory factory = mock(DefaultArmoryFactory.class);
        InMemoryRunner firstRunner = mock(InMemoryRunner.class);
        InMemoryRunner secondRunner = mock(InMemoryRunner.class);
        BaseSessionService firstSessions = mock(BaseSessionService.class);
        BaseSessionService secondSessions = mock(BaseSessionService.class);
        Session first = mock(Session.class);
        Session second = mock(Session.class);
        when(first.id()).thenReturn("session-one");
        when(second.id()).thenReturn("session-two");
        when(firstRunner.sessionService()).thenReturn(firstSessions);
        when(secondRunner.sessionService()).thenReturn(secondSessions);
        when(firstSessions.createSession("wf_one", "user")).thenReturn(Single.just(first));
        when(secondSessions.createSession("wf_two", "user")).thenReturn(Single.just(second));
        when(factory.getAiAgentRegisterVO("wf_one")).thenReturn(AiAgentRegisterVO.builder()
                .appName("wf_one").workflowId("wf_one").runner(firstRunner).build());
        when(factory.getAiAgentRegisterVO("wf_two")).thenReturn(AiAgentRegisterVO.builder()
                .appName("wf_two").workflowId("wf_two").runner(secondRunner).build());
        ChatService chatService = new ChatService();
        ReflectionTestUtils.setField(chatService, "defaultArmoryFactory", factory);

        Assert.assertEquals("session-one", chatService.createSession("wf_one", "user"));
        Assert.assertEquals("session-two", chatService.createSession("wf_two", "user"));
    }
}
