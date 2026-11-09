package cn.bugstack.ai.test.infrastructure.agent;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamControlRoleEnum;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.model.DynamicTeamControlModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

class DynamicTeamControlModelPortTest {

    @Test
    void rejectsSuccessfulHttpCallWithoutModelText() {
        DynamicTeamControlModelPort port = new DynamicTeamControlModelPort(mock(ChatModel.class));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> port.generate(DynamicTeamControlRoleEnum.PLANNER, "system", "goal"));

        assertTrue(failure.getCause().getMessage().contains("返回内容为空"));
    }

    @Test
    void rejectsBlankModelText() {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn("  ");
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);

        DynamicTeamControlModelPort port = new DynamicTeamControlModelPort(model);
        assertThrows(IllegalStateException.class,
                () -> port.generate(DynamicTeamControlRoleEnum.PLANNER, "system", "goal"));
    }
}
