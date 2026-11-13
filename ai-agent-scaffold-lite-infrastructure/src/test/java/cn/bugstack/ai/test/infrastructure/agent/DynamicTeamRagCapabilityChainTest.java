package cn.bugstack.ai.test.infrastructure.agent;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationResultVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningModeEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamWorkerVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamPromptBundleVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.worker.DynamicTeamWorkerRuntimeEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamModelCallBudgetEntity;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanFactory;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidationService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidator;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.DynamicTeamPlanContextValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.DynamicTeamPlanDependencyValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.DynamicTeamPlanTaskValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.rule.DynamicTeamPlanWorkerValidationRule;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.domain.rag.service.IDocumentRagService;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamAdkWorkerAgentHandle;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamRagSearchFunctions;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamRagSearchToolFactory;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamWorkerAgentFactory;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 不打真实模型/向量库：用语料文件 + Mock 检索，验证
 * 「计划申报 rag_search → 校验通过 → Worker 挂上工具 → 检索只打白名单文档」。
 */
class DynamicTeamRagCapabilityChainTest {

    private static final String DOCUMENT_ID = "project-architecture";
    private static final Path CORPUS = Path.of(
            "docs", "rag-corpus", "project-architecture.md");

    private DynamicTeamPlanValidationService validationService;
    private String corpusText;

    @BeforeEach
    void setUp() throws Exception {
        Path corpusPath = resolveCorpusPath();
        corpusText = Files.readString(corpusPath, StandardCharsets.UTF_8);
        assertTrue(corpusText.contains("rag_search"));
        assertTrue(corpusText.contains("/team/"));

        DynamicTeamPlanValidator validator = new DynamicTeamPlanValidator(
                new DynamicTeamPlanContextValidationRule(),
                new DynamicTeamPlanWorkerValidationRule(),
                new DynamicTeamPlanTaskValidationRule(),
                new DynamicTeamPlanDependencyValidationRule());
        validationService = new DynamicTeamPlanValidationService();
        ReflectionTestUtils.setField(validationService, "planValidator", validator);
        ReflectionTestUtils.setField(validationService, "planFactory",
                new DynamicTeamPlanFactory());
    }

    @Test
    void planToMountedToolToScopedSearch() {
        DynamicTeamPlanValidationResultVO validated = validationService.validateAndBuild(
                DynamicTeamPlanValidationCommandEntity.builder()
                        .runId("run_rag_chain")
                        .planningMode(DynamicTeamPlanningModeEnum.INITIAL)
                        .planDraft(draftWithRagWorker())
                        .runtimePolicy(policy())
                        .build());
        assertTrue(validated.isValid());
        DynamicTeamWorkerVO researchWorker = validated.getAcceptedPlan().getWorkers().get(0);
        DynamicTeamWorkerVO writerWorker = validated.getAcceptedPlan().getWorkers().get(1);
        assertEquals(List.of("rag_search"), researchWorker.getNeededCapabilities());
        assertEquals(List.of(), writerWorker.getNeededCapabilities());

        IDocumentRagService ragService = mock(IDocumentRagService.class);
        when(ragService.searchSimilarChunks(
                eq("Worker 默认有没有工具"), eq(5), isNull(),
                eq(List.of(DOCUMENT_ID))))
                .thenReturn(List.of(SimilarDocumentChunkVO.builder()
                        .fileName("project-architecture.md")
                        .similarity(0.95d)
                        .chunk(DocumentChunkEntity.builder()
                                .documentId(DOCUMENT_ID)
                                .chunkId("fact-worker-tools")
                                .content(extractFactSentence(corpusText,
                                        "Worker 默认没有工具"))
                                .build())
                        .build()));

        DynamicTeamRagSearchToolFactory toolFactory = new DynamicTeamRagSearchToolFactory();
        ReflectionTestUtils.setField(toolFactory, "documentRagService", ragService);
        ReflectionTestUtils.setField(toolFactory, "runtimePolicy", policy());

        DynamicTeamWorkerAgentFactory workerFactory = new DynamicTeamWorkerAgentFactory();
        ReflectionTestUtils.setField(workerFactory, "chatModel", mock(ChatModel.class));
        ReflectionTestUtils.setField(workerFactory, "promptBundle",
                DynamicTeamPromptBundleVO.builder()
                        .workerInstruction("固定 Worker 系统提示")
                        .build());
        ReflectionTestUtils.setField(workerFactory, "ragSearchToolFactory", toolFactory);

        List<BaseTool> researchTools = workerFactory.resolveTools(researchWorker);
        List<BaseTool> writerTools = workerFactory.resolveTools(writerWorker);
        assertEquals(1, researchTools.size());
        assertTrue(writerTools.isEmpty());

        DynamicTeamWorkerRuntimeEntity runtime = workerFactory.create(
                "run_rag_chain", 1, researchWorker,
                new DynamicTeamModelCallBudgetEntity(5));
        try {
            DynamicTeamAdkWorkerAgentHandle handle =
                    (DynamicTeamAdkWorkerAgentHandle) ReflectionTestUtils.getField(
                            runtime, "agentHandle");
            LlmAgent agent = handle.requireAgent();
            assertEquals(1, agent.tools().size());
            assertEquals(
                    DynamicTeamRagSearchFunctions.TOOL_NAME,
                    agent.tools().get(0).name());
        } finally {
            runtime.close();
        }

        DynamicTeamRagSearchFunctions functions = new DynamicTeamRagSearchFunctions(
                ragService, List.of(DOCUMENT_ID));
        Map<String, Object> searchResult = functions.ragSearch("Worker 默认有没有工具");
        assertEquals(true, searchResult.get("ok"));
        assertEquals(1, searchResult.get("hitCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snippets =
                (List<Map<String, Object>>) searchResult.get("snippets");
        assertFalse(snippets.isEmpty());
        assertTrue(String.valueOf(snippets.get(0).get("text")).contains("neededCapabilities")
                || String.valueOf(snippets.get(0).get("text")).contains("rag_search"));
    }

    private DynamicTeamPlanDraftVO draftWithRagWorker() {
        List<DynamicTeamPlanDraftVO.WorkerDraft> workers = new ArrayList<>();
        workers.add(new DynamicTeamPlanDraftVO.WorkerDraft(
                "researcher", "调研员", "核对项目事实", "先检索再结论",
                List.of("rag_search")));
        workers.add(new DynamicTeamPlanDraftVO.WorkerDraft(
                "writer", "撰写员", "整理文案", "不负责检索", List.of()));

        List<DynamicTeamPlanDraftVO.TaskDraft> tasks = new ArrayList<>();
        tasks.add(new DynamicTeamPlanDraftVO.TaskDraft(
                "research_facts", "researcher", "核对 Worker 工具边界",
                "回答 Worker 默认是否有工具", "含依据的结论",
                List.of("结论可对照项目架构说明"), List.of()));
        tasks.add(new DynamicTeamPlanDraftVO.TaskDraft(
                "write_summary", "writer", "整理表述",
                "把调研结论写成简短段落", "一段可读说明",
                List.of("不新增未经调研的事实"), List.of("research_facts")));

        return new DynamicTeamPlanDraftVO(
                "1.0", "调研后撰写", "INITIAL_PLAN", workers, tasks);
    }

    private DynamicTeamRuntimePolicyVO policy() {
        return DynamicTeamRuntimePolicyVO.builder()
                .enabled(true)
                .maxWorkers(5)
                .maxTasks(20)
                .maxConcurrentWorkers(3)
                .maxReplans(2)
                .stepTimeoutSeconds(60)
                .runTimeoutSeconds(300)
                .maxModelCalls(25)
                .maxUserInputCharacters(20000)
                .ragDocumentIds(List.of(DOCUMENT_ID))
                .build();
    }

    private Path resolveCorpusPath() {
        Path fromModule = Path.of("..", "docs", "rag-corpus", "project-architecture.md")
                .normalize();
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        Path fromRoot = CORPUS;
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        throw new IllegalStateException(
                "找不到语料文件 docs/rag-corpus/project-architecture.md");
    }

    private String extractFactSentence(String text, String marker) {
        for (String line : text.split("\\R")) {
            if (line.contains(marker)) {
                return line.replaceFirst("^\\d+\\.\\s*", "").trim();
            }
        }
        throw new IllegalStateException("语料中找不到标记：" + marker);
    }

}
