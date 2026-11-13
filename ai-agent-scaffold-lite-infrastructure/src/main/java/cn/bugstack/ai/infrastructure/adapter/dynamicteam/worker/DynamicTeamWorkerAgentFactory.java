package cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamWorkerAgentFactory;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamModelCallBudgetEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamPromptBundleVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;
import cn.bugstack.ai.domain.agent.service.armory.matter.patch.MySpringAI;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 按正式计划中的 neededCapabilities 为临时 Worker 装配已授权工具。
 */
@Service
public class DynamicTeamWorkerAgentFactory
        implements IDynamicTeamWorkerAgentFactory {

    private static final String WORKER_DESCRIPTION = "动态团队临时 Worker";

    @Resource(name = "ragChatModel")
    private ChatModel chatModel;

    @Resource
    private DynamicTeamPromptBundleVO promptBundle;

    @Resource
    private DynamicTeamRagSearchToolFactory ragSearchToolFactory;

    @Override
    public DynamicTeamWorkerRuntimeEntity create(
            String runId,
            int createdPlanVersion,
            DynamicTeamWorkerVO worker,
            DynamicTeamModelCallBudgetEntity modelCallBudget) {
        validateRequest(runId, createdPlanVersion, worker);

        String runtimeAgentName = buildRuntimeAgentName(
                runId, createdPlanVersion, worker.getWorkerId());

        LlmAgent agent = LlmAgent.builder()
                .name(runtimeAgentName)
                .description(WORKER_DESCRIPTION)
                .model(new DynamicTeamBudgetGuardedLlm(
                        new MySpringAI(chatModel), modelCallBudget))
                .instruction(promptBundle.getWorkerInstruction())
                .tools(resolveTools(worker))
                .includeContents(LlmAgent.IncludeContents.NONE)
                .planning(false)
                .disallowTransferToParent(true)
                .disallowTransferToPeers(true)
                .build();

        return new DynamicTeamWorkerRuntimeEntity(
                runId, createdPlanVersion, worker,
                new DynamicTeamAdkWorkerAgentHandle(agent));
    }

    /**
     * 仅装配计划已声明且服务端认识的能力；未声明则工具列表为空。
     */
    public List<BaseTool> resolveTools(DynamicTeamWorkerVO worker) {
        List<BaseTool> tools = new ArrayList<>();
        List<String> capabilities = worker.getNeededCapabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        if (capabilities.contains(DynamicTeamRagSearchFunctions.TOOL_NAME)) {
            tools.add(ragSearchToolFactory.createTool());
        }
        return List.copyOf(tools);
    }

    private void validateRequest(
            String runId,
            int createdPlanVersion,
            DynamicTeamWorkerVO worker) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空。");
        }
        if (createdPlanVersion <= 0) {
            throw new IllegalArgumentException("Worker 创建计划版本必须大于 0。");
        }
        if (worker == null
                || worker.getWorkerId() == null
                || worker.getWorkerId().isBlank()) {
            throw new IllegalArgumentException("Worker 定义和 workerId 不能为空。");
        }
        if (promptBundle == null
                || promptBundle.getWorkerInstruction() == null
                || promptBundle.getWorkerInstruction().isBlank()) {
            throw new IllegalStateException("固定 Worker 提示词不能为空。");
        }
    }

    private String buildRuntimeAgentName(
            String runId,
            int planVersion,
            String workerId) {
        String runHash = UUID.nameUUIDFromBytes(
                        runId.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return "dt_" + runHash + "_p" + planVersion + "_" + workerId;
    }

}
