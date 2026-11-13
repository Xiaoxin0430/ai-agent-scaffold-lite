# 动态团队项目架构说明（RAG 评测语料）

> 文档 ID（入库 documentId）：`project-architecture`  
> 用途：供动态团队 `rag_search` 检索；内容需与当前代码事实一致，评测题应能从本文核对。

## 模块

- `api`：HTTP 契约与 DTO
- `trigger`：Controller 与协议转换
- `domain`：动态团队、Armory、RAG 领域逻辑
- `infrastructure`：模型、文档解析、向量库、ADK Worker 适配
- `app`：启动与配置
- `web`：动态团队控制台，入口 `/team/`

## 动态团队主链路

一次运行的状态顺序为：

`PLANNING` → `VALIDATING` → `CREATING` → `EXECUTING` → `SUMMARIZING` → `COMPLETED`

当前 V1 在任务全部成功后直接汇总，**不经过** `REVIEWING` / `REPLANNING`。

## Worker 与工具

- 临时 Worker 由 `DynamicTeamWorkerAgentFactory` 按正式计划创建，运行结束清理。
- Planner 可在 Worker 上声明 `neededCapabilities`。
- 本阶段白名单仅允许能力名：`rag_search`。
- 仅当计划包含 `rag_search` 时，后端才会为该 Worker 装配文档检索工具；未声明则工具列表为空。
- `rag_search` 只能检索服务端配置的文档 ID 白名单（默认包含 `project-architecture`），模型不能自行扩大范围。

## 明确未实现（不得写成已有）

- Reviewer 质量评审与自动 Replan（`max-replans` 仅为预留）
- 运行状态持久化与进程重启恢复
- 服务端鉴权与用户隔离（演示 Cookie 登录）
- 系统内完整 Token Trace 账本（可后续补充）

## 可核对事实句（评测用）

1. 动态团队控制台路径是 `/team/`。
2. Worker 默认没有工具；需要 `neededCapabilities` 含 `rag_search` 才会装配检索。
3. 当前执行完成后进入汇总，跳过评审状态。
