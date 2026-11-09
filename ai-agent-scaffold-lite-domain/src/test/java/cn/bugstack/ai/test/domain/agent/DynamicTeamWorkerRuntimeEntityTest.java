package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamWorkerAgentHandle;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicTeamWorkerRuntimeEntityTest {

    @Test
    void closingRuntimeClosesItsAgentHandleExactlyOnce() {
        TestHandle handle = new TestHandle();
        DynamicTeamWorkerRuntimeEntity runtime = new DynamicTeamWorkerRuntimeEntity(
                "run_1", 1,
                DynamicTeamWorkerVO.builder().workerId("worker_1")
                        .roleName("分析员").responsibility("分析")
                        .instruction("完成任务").build(),
                handle);

        assertEquals("agent_1", runtime.getRuntimeAgentName());
        assertSame(handle, runtime.requireAgentHandle());
        runtime.close();
        runtime.close();

        assertTrue(runtime.isClosed());
        assertEquals(1, handle.closeCount);
        assertThrows(IllegalStateException.class, runtime::requireAgentHandle);
    }

    private static final class TestHandle implements IDynamicTeamWorkerAgentHandle {
        private int closeCount;

        @Override
        public String getRuntimeAgentName() {
            return "agent_1";
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
