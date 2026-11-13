package cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker;

import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.domain.rag.service.IDocumentRagService;
import com.google.adk.tools.Annotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态团队临时 Worker 的项目文档检索函数。
 * 检索范围由服务端配置的 documentId 白名单锁定，模型不能扩大范围。
 */
public class DynamicTeamRagSearchFunctions {

    public static final String TOOL_NAME = "rag_search";

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_SNIPPET_CHARACTERS = 500;

    private final IDocumentRagService documentRagService;
    private final List<String> allowedDocumentIds;

    public DynamicTeamRagSearchFunctions(
            IDocumentRagService documentRagService,
            List<String> allowedDocumentIds) {
        if (documentRagService == null) {
            throw new IllegalArgumentException("documentRagService 不能为空。");
        }
        this.documentRagService = documentRagService;
        this.allowedDocumentIds = allowedDocumentIds == null
                ? List.of()
                : List.copyOf(allowedDocumentIds);
    }

    @Annotations.Schema(
            name = TOOL_NAME,
            description = "在服务端授权的项目文档范围内检索相关片段。"
                    + "涉及本项目架构、接口或运行边界等事实时必须先调用本工具，"
                    + "不能扩大检索范围或编造未检索到的内容。")
    public Map<String, Object> ragSearch(
            @Annotations.Schema(
                    name = "query",
                    description = "检索问句，应具体描述需要核对的事实或主题。")
            String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (allowedDocumentIds.isEmpty()) {
            result.put("ok", false);
            result.put("error", "服务端未配置可检索的项目文档范围。");
            return result;
        }
        if (query == null || query.isBlank()) {
            result.put("ok", false);
            result.put("error", "query 不能为空。");
            return result;
        }

        try {
            List<SimilarDocumentChunkVO> hits = documentRagService.searchSimilarChunks(
                    query.trim(), DEFAULT_TOP_K, null, allowedDocumentIds);
            result.put("ok", true);
            result.put("documentScope", allowedDocumentIds);
            result.put("hitCount", hits == null ? 0 : hits.size());
            result.put("snippets", formatSnippets(hits));
            if (hits == null || hits.isEmpty()) {
                result.put("message", "未检索到相关片段，请明确说明依据不足，不要编造。");
            }
            return result;
        } catch (RuntimeException exception) {
            result.put("ok", false);
            result.put("error", exception.getMessage() == null
                    ? "检索失败。"
                    : exception.getMessage());
            return result;
        }
    }

    List<String> getAllowedDocumentIds() {
        return allowedDocumentIds;
    }

    private List<Map<String, Object>> formatSnippets(List<SimilarDocumentChunkVO> hits) {
        List<Map<String, Object>> snippets = new ArrayList<>();
        if (hits == null) {
            return snippets;
        }
        for (SimilarDocumentChunkVO hit : hits) {
            if (hit == null || hit.getChunk() == null) {
                continue;
            }
            DocumentChunkEntity chunk = hit.getChunk();
            Map<String, Object> snippet = new LinkedHashMap<>();
            snippet.put("documentId", chunk.getDocumentId());
            snippet.put("chunkId", chunk.getChunkId());
            snippet.put("fileName", hit.getFileName());
            snippet.put("similarity", hit.getSimilarity());
            snippet.put("text", truncate(chunk.getContent()));
            snippets.add(snippet);
        }
        return snippets;
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_SNIPPET_CHARACTERS) {
            return content;
        }
        return content.substring(0, MAX_SNIPPET_CHARACTERS) + "...";
    }

}
