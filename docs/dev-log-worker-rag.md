# 开发记录：动态团队 Worker 受控 RAG

> 面试前口述复习用。事实以代码为准；联调结果见文末。

## 1. 这次做了什么（按步骤）

1. **计划协议**：Worker 增加 `neededCapabilities`；Planner 提示要求需要查项目文档时填 `["rag_search"]`，否则 `[]`。`DynamicTeamPlanFactory` 拷到正式计划。
2. **校验白名单**：`DynamicTeamPlanWorkerValidationRule` 只允许 `rag_search`；空/缺省合法；非法名、空串、重复会拦下。
3. **检索工具**：`DynamicTeamRagSearchFunctions` + `DynamicTeamRagSearchToolFactory` 做成 ADK `FunctionTool`；内部调 `IDocumentRagService.searchSimilarChunks`，**强制**带配置里的 `rag-document-ids`（默认 `project-architecture`）。
4. **按需装配**：`DynamicTeamWorkerAgentFactory` 看正式计划：有 `rag_search` 才 `createTool()` 挂上；没有则 `tools` 为空。更新 `worker.md`：有工具先查再写。
5. **语料与测试**：新增 `docs/rag-corpus/project-architecture.md`；单测覆盖校验/工具/装配/链路。**Trace 按约定先不做。**

联调前用 `connect-server.cmd`（实际跑 `scripts/dev/start-pgvector-tunnel.ps1`）打通 `127.0.0.1:15432` 到云上 pgvector，再本地起 `ai-agent-scaffold-lite-app`。

## 2. 口述一段话（怎么给 Worker 加 RAG、RAG 怎么实现）

我们给动态团队加的不是「人人都能查库」，而是**受控能力装配**。Planner 在计划 JSON 里给每个临时 Worker 填 `neededCapabilities`；后端校验只认白名单里的 `rag_search`。校验通过后，`DynamicTeamWorkerAgentFactory` 创建 ADK 的 `LlmAgent` 时，如果这个 Worker 申报了 `rag_search`，就向 `DynamicTeamRagSearchToolFactory` 要一把工具挂上去，否则工具列表为空——权限靠装没装上保证，不只靠提示词。这把工具底层走现成的文档 RAG：上传文档时解析、清洗、按 Token 切分、Embedding 写入 PostgreSQL/pgvector；检索时把 query 向量化，在**配置允许的 documentId**范围内做相似检索，把片段返回给模型。所以面试可以强调三层：能力声明、白名单校验、创建时装配；以及 RAG 管道：入库向量化与运行时受限检索。

## 3. 关键类与数据流（速记）

```text
Planner JSON
  → WorkerDraft.neededCapabilities
  → 校验（仅 rag_search）
  → PlanFactory → WorkerVO
  → WorkerAgentFactory.resolveTools
       → RagSearchToolFactory.createTool()  // 造的是 Tool，不是 Agent
  → LlmAgent.tools([...])
  → 执行任务时模型可调用 rag_search
       → IDocumentRagService.searchSimilarChunks(query, topK, null, allowedDocumentIds)
```

易混点：`RagSearchToolFactory.createTool()` 造的是 **FunctionTool**；`WorkerAgentFactory.create()` 才造 **LlmAgent**，并决定挂不挂工具。

## 4. 一次真实联调（2026-11-13）

| 步骤 | 结果 |
|---|---|
| SSH 隧道 | `start-pgvector-tunnel.ps1` → `127.0.0.1:15432` OK |
| 应用启动 | `Started Application`，端口 8091 |
| 入库 | `POST /api/v1/rag/documents/project-architecture/index` → chunkCount=2，成功 |
| 检索冒烟 | `POST /api/v1/rag/search` 命中语料中的 `/team/`、`neededCapabilities` 等事实 |
| 动态团队 | runId `4beb83ae-d875-40e6-ae09-ca298606ba6b`，`COMPLETED` / `NORMAL_COMPLETION`，4 次模型调用 |
| 计划 | `doc_researcher` → `neededCapabilities: [rag_search]`；`answer_writer` → 空 |
| 答案 | 正确写出 `/team/`、默认无工具需声明 `rag_search`、跳过 REVIEWING；并引用语料 |

快照备份：`data/dev/e2e-run-snapshot.json`（勿提交密钥；该文件仅为本地联调产物）。

## 5. 已知边界

- 未做系统内 Trace；Token 细账仍可能依赖供应商控制台。
- 演示登录、运行内存态、无 Reviewer/Replan——与 `Target.md` 一致。
- 本地不需自建 `.env.pgvector`；连库走隧道 + 根目录 `.env` 中的 `RAG_DB_*`。
