package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.agent.model.valobj.RagRouteDecision;
import cn.bugstack.ai.infrastructure.adapter.port.LlmRagRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

public class LlmRagRouterTest {

    @Test
    public void test_direct_and_rag_decisions() {
        ChatModel model = Mockito.mock(ChatModel.class);
        Mockito.when(model.call(Mockito.anyString()))
                .thenReturn("{\"route\":\"DIRECT\",\"searchQuery\":\"\"}")
                .thenReturn("{\"route\":\"RAG\",\"searchQuery\":\"报销期限\"}");
        LlmRagRouter router = new LlmRagRouter(model, new ObjectMapper());
        ReflectionTestUtils.setField(router, "enabled", true);

        Assert.assertEquals(RagRouteDecision.builder().retrieve(false).searchQuery("").build(), router.decide("你好", ""));
        Assert.assertEquals(RagRouteDecision.builder().retrieve(true).searchQuery("报销期限").build(),
                router.decide("它的期限呢？", "刚才回答了报销规定"));
    }

    @Test
    public void test_invalid_model_output_defaults_to_retrieval() {
        ChatModel model = Mockito.mock(ChatModel.class);
        Mockito.when(model.call(Mockito.anyString())).thenReturn("无法判断");
        LlmRagRouter router = new LlmRagRouter(model, new ObjectMapper());
        ReflectionTestUtils.setField(router, "enabled", true);

        Assert.assertEquals(RagRouteDecision.builder().retrieve(true).searchQuery("报销规则是什么").build(),
                router.decide("报销规则是什么", ""));
    }
}
