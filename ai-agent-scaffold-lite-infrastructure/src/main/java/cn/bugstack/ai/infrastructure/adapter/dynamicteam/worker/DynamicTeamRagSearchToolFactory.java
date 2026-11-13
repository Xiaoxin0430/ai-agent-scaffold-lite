package cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.rag.service.IDocumentRagService;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 按运行策略中的文档范围创建 rag_search 工具。
 */
@Service
public class DynamicTeamRagSearchToolFactory {

    @Resource
    private IDocumentRagService documentRagService;

    @Resource
    private DynamicTeamRuntimePolicyVO runtimePolicy;

    public BaseTool createTool() {
        List<String> documentIds = runtimePolicy == null
                ? List.of()
                : runtimePolicy.getRagDocumentIds();
        DynamicTeamRagSearchFunctions functions = new DynamicTeamRagSearchFunctions(
                documentRagService, documentIds);
        return FunctionTool.create(functions, "ragSearch");
    }

}
