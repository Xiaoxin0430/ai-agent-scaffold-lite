package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.service.chat.RagChatContextBuilder;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RagAgentContextTest {

    @Test
    public void test_evidence_reaches_model_without_replacing_user_message() {
        RecordingLlm model = new RecordingLlm();
        LlmAgent agent = LlmAgent.builder()
                .name("testAgent")
                .model(model)
                .instruction("回答用户问题")
                .beforeModelCallbackSync((context, request) -> {
                    Object evidence = context.state().get(RagChatContextBuilder.STATE_KEY);
                    if (evidence instanceof String text && !text.isBlank()) {
                        request.appendInstructions(List.of(text));
                    }
                    return Optional.empty();
                })
                .build();
        InMemoryRunner runner = new InMemoryRunner(agent);
        Session session = runner.sessionService().createSession(runner.appName(), "admin").blockingGet();

        runner.runAsync("admin", session.id(), Content.fromParts(Part.fromText("报销期限？")),
                RunConfig.builder().build(), Map.of(RagChatContextBuilder.STATE_KEY, "资料：四天 [S1]"))
                .blockingForEach(event -> {});

        Assert.assertEquals(1, model.requests.size());
        Assert.assertTrue(model.requests.get(0).getSystemInstructions().stream()
                .anyMatch(text -> text.contains("资料：四天 [S1]")));
        Session saved = runner.sessionService().getSession(runner.appName(), "admin", session.id(), Optional.empty())
                .blockingGet();
        String userText = saved.events().stream().filter(event -> "user".equals(event.author()))
                .findFirst().flatMap(event -> event.content())
                .flatMap(content -> content.parts())
                .map(parts -> parts.get(0).text().orElse(""))
                .orElse("");
        Assert.assertEquals("报销期限？", userText);

        runner.runAsync("admin", session.id(), Content.fromParts(Part.fromText("你好")),
                RunConfig.builder().build(), Map.of(RagChatContextBuilder.STATE_KEY, ""))
                .blockingForEach(event -> {});
        Assert.assertEquals(2, model.requests.size());
        Assert.assertFalse(model.requests.get(1).getSystemInstructions().stream()
                .anyMatch(text -> text.contains("资料：四天 [S1]")));
    }

    private static class RecordingLlm extends BaseLlm {
        private final List<LlmRequest> requests = new ArrayList<>();

        private RecordingLlm() {
            super("test-model");
        }

        @Override
        public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
            requests.add(request);
            return Flowable.just(LlmResponse.builder()
                    .content(Content.builder().role("model").parts(List.of(Part.fromText("四天。"))).build())
                    .turnComplete(true)
                    .build());
        }

        @Override
        public BaseLlmConnection connect(LlmRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
