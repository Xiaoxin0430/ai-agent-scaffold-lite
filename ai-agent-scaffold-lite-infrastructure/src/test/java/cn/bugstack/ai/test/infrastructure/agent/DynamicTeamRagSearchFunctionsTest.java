package cn.bugstack.ai.test.infrastructure.agent;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.domain.rag.service.IDocumentRagService;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamRagSearchFunctions;
import cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker.DynamicTeamRagSearchToolFactory;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.FunctionDeclaration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicTeamRagSearchFunctionsTest {

    @Test
    void shouldRejectBlankQueryWithoutCallingRag() {
        IDocumentRagService ragService = mock(IDocumentRagService.class);
        DynamicTeamRagSearchFunctions functions = new DynamicTeamRagSearchFunctions(
                ragService, List.of("project-architecture"));

        Map<String, Object> result = functions.ragSearch("  ");

        assertEquals(false, result.get("ok"));
        verify(ragService, never()).searchSimilarChunks(
                any(), any(), any(), anyList());
    }

    @Test
    void shouldRejectWhenDocumentScopeNotConfigured() {
        IDocumentRagService ragService = mock(IDocumentRagService.class);
        DynamicTeamRagSearchFunctions functions = new DynamicTeamRagSearchFunctions(
                ragService, List.of());

        Map<String, Object> result = functions.ragSearch("动态团队状态机");

        assertEquals(false, result.get("ok"));
        assertTrue(String.valueOf(result.get("error")).contains("未配置"));
        verify(ragService, never()).searchSimilarChunks(
                any(), any(), any(), anyList());
    }

    @Test
    void shouldSearchOnlyAllowedDocumentIds() {
        IDocumentRagService ragService = mock(IDocumentRagService.class);
        when(ragService.searchSimilarChunks(
                eq("Worker 是否有工具"), eq(5), isNull(),
                eq(List.of("project-architecture"))))
                .thenReturn(List.of(SimilarDocumentChunkVO.builder()
                        .fileName("project-architecture.md")
                        .similarity(0.91d)
                        .chunk(DocumentChunkEntity.builder()
                                .documentId("project-architecture")
                                .chunkId("c1")
                                .content("Worker 默认无工具，需按 neededCapabilities 装配。")
                                .build())
                        .build()));

        DynamicTeamRagSearchFunctions functions = new DynamicTeamRagSearchFunctions(
                ragService, List.of("project-architecture"));

        Map<String, Object> result = functions.ragSearch("Worker 是否有工具");

        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("hitCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snippets =
                (List<Map<String, Object>>) result.get("snippets");
        assertEquals(1, snippets.size());
        assertTrue(String.valueOf(snippets.get(0).get("text"))
                .contains("neededCapabilities"));
        verify(ragService).searchSimilarChunks(
                eq("Worker 是否有工具"), eq(5), isNull(),
                eq(List.of("project-architecture")));
    }

    @Test
    void shouldSurfaceRagFailuresAsToolErrors() {
        IDocumentRagService ragService = mock(IDocumentRagService.class);
        when(ragService.searchSimilarChunks(any(), any(), any(), anyList()))
                .thenThrow(new IllegalArgumentException("topK too large"));
        DynamicTeamRagSearchFunctions functions = new DynamicTeamRagSearchFunctions(
                ragService, List.of("project-architecture"));

        Map<String, Object> result = functions.ragSearch("任意问题");

        assertEquals(false, result.get("ok"));
        assertEquals("topK too large", result.get("error"));
    }

    @Test
    void factoryShouldExposeRagSearchToolName() {
        DynamicTeamRagSearchToolFactory factory = new DynamicTeamRagSearchToolFactory();
        ReflectionTestUtils.setField(factory, "documentRagService",
                mock(IDocumentRagService.class));
        ReflectionTestUtils.setField(factory, "runtimePolicy",
                DynamicTeamRuntimePolicyVO.builder()
                        .ragDocumentIds(List.of("project-architecture"))
                        .build());

        BaseTool tool = factory.createTool();

        assertTrue(tool instanceof FunctionTool);
        FunctionDeclaration declaration = tool.declaration().orElseThrow();
        assertEquals(
                DynamicTeamRagSearchFunctions.TOOL_NAME,
                declaration.name().orElseThrow());
        assertFalse(factory.createTool() == tool);
    }

}
