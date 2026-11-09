package cn.bugstack.ai.test.infrastructure.agent;

import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamTaskExecutionPort;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamAdkWorkerAgentHandle;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.*;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import static cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskStatusEnum.SUCCEEDED;
import static org.junit.jupiter.api.Assertions.*;
import cn.bugstack.ai.domain.agent.model.dynamicteam.execution.DynamicTeamTaskExecutionResultVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamTaskVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;

@Timeout(5)
class DynamicTeamTaskExecutionPortTest {
    @Test
    void passesTaskAndDependenciesAsDataAndUsesFreshSession() throws Exception {
        List<LlmRequest> requests = new ArrayList<>();
        DynamicTeamWorkerRuntimeEntity worker = worker(requests, "answer");
        DynamicTeamTaskExecutionPort port = new DynamicTeamTaskExecutionPort();
        DynamicTeamTaskVO task = task();
        Map<String, DynamicTeamTaskExecutionResultVO> upstream = Map.of("a",
                DynamicTeamTaskExecutionResultVO.builder().taskId("a").workerId("other")
                        .status(SUCCEEDED).output("upstream_evidence").build());
        assertTrue(port.execute("user_goal", task, worker, upstream).contains("answer"));
        assertTrue(port.execute("second_goal", task, worker, upstream).contains("answer"));
        assertEquals(2, requests.size());
        assertEquals(1, requests.get(1).contents().size());
        String input = requests.get(0).contents().get(0).parts().orElseThrow().get(0).text().orElseThrow();
        assertTrue(input.contains("user_goal"));
        assertTrue(input.contains("worker_business_instruction"));
        assertTrue(input.contains("acceptance_marker"));
        assertTrue(input.contains("upstream_evidence"));
        assertFalse(String.join("", requests.get(0).getSystemInstructions()).contains("worker_business_instruction"));
        assertThrows(IllegalArgumentException.class, () -> port.execute("goal", task, worker, Map.of()));
        worker.close();
    }

    @Test
    void rejectsEmptyFinalOutput() {
        DynamicTeamWorkerRuntimeEntity worker = worker(new ArrayList<>(), "");
        DynamicTeamTaskVO task = DynamicTeamTaskVO.builder().taskId("empty").workerId("w")
                .title("empty").description("description").expectedOutput("text")
                .acceptanceCriteria(List.of("complete")).dependsOn(List.of()).build();
        try {
            assertThrows(IllegalStateException.class, () -> new DynamicTeamTaskExecutionPort()
                    .execute("goal", task, worker, Map.of()));
        } finally {
            worker.close();
        }
    }

    private DynamicTeamTaskVO task() {
        return DynamicTeamTaskVO.builder().taskId("b").workerId("w").title("title")
                .description("description").expectedOutput("output")
                .acceptanceCriteria(List.of("acceptance_marker")).dependsOn(List.of("a")).build();
    }

    private DynamicTeamWorkerRuntimeEntity worker(List<LlmRequest> requests, String answer) {
        BaseLlm model = new BaseLlm("fake") {
            @Override
            public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
                requests.add(request);
                return Flowable.just(LlmResponse.builder().content(Content.builder().role("model")
                        .parts(List.of(Part.fromText(answer))).build()).turnComplete(true).build());
            }
            @Override
            public BaseLlmConnection connect(LlmRequest request) { throw new UnsupportedOperationException(); }
        };
        return new DynamicTeamWorkerRuntimeEntity("run", 1, DynamicTeamWorkerVO.builder().workerId("w")
                .roleName("role").responsibility("responsibility").instruction("worker_business_instruction").build(),
                new DynamicTeamAdkWorkerAgentHandle(
                        LlmAgent.builder().name("fake_worker").model(model).instruction("fixed_system")
                                .includeContents(LlmAgent.IncludeContents.NONE).tools(List.of()).build()));
    }
}
