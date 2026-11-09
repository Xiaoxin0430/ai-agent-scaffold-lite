# AI Agent Scaffold Lite

以动态多智能体团队为核心的 Agent 工程实验项目。用户提交目标和验收要求后，系统规划临时 Worker 与任务依赖，校验计划、调度执行、汇总结果，并通过事件和运行控制台展示过程。

> 当前代码提供可运行的初始轮执行链，已用真实模型（DeepSeek）完成 5 个用例的冒烟验证。结果质量、系统性效果评测与生产部署尚未完成验收。项目目标与进度见 [Target.md](Target.md)。

## 核心功能

- **动态规划：**Planner 生成 Worker、任务和依赖；后端严格解析输出，并检查成员、任务、引用与依赖环，再生成带版本的正式计划。
- **临时团队：**按正式计划创建仅属于本次运行的 Worker。当前 Worker 使用后端固定模型和提示词，不装配 MCP、Skills 或其他工具；运行结束清理成员引用。
- **任务调度：**按依赖关系解锁任务，允许不同 Worker 并行、同一 Worker 串行。失败阻断相关下游任务；超时、取消或额度耗尽时停止后续调度并记录原因。
- **结果汇总：**正式计划中的任务全部成功后，Summarizer 根据目标、计划与任务结果生成最终回答。
- **运行观察：**提供异步启动、快照、取消、结果查询和 SSE 事件流；控制台显示团队成员、任务状态、依赖、执行动态与最终结果。

当前运行只执行初始计划。Reviewer 评审和自动重新规划尚未接入运行链路；任务正常返回文本不代表内容已通过质量评审。

## 保留的底层能力

- **Agent 装配：**保留配置驱动的模型、Agent、Runner、顺序/并行/循环工作流装配能力，以及一份单 Agent 调试配置。它是基础能力验证入口，不是项目主业务。
- **RAG：**保留文档解析、清洗、按 Token 切分、Embedding、PostgreSQL/pgvector 索引和相似检索。当前动态 Worker 尚未调用 RAG；文档入库与检索接口用于开发验证。
- **MCP 与 Skills：**保留本地、SSE、Stdio MCP 客户端和资源型 Skills 的底层构建实现。动态 Worker 尚未获得这些工具权限。
- **普通对话：**保留智能体查询、会话创建和非流式对话接口，用于验证基础装配；不再维护独立聊天页面与自动 RAG 路由。

## 模块结构

| 目录 | 职责 |
|---|---|
| `ai-agent-scaffold-lite-api` | HTTP 接口及 DTO |
| `ai-agent-scaffold-lite-trigger` | HTTP 入口与协议转换 |
| `ai-agent-scaffold-lite-domain` | 动态团队、Agent 装配和 RAG 领域逻辑 |
| `ai-agent-scaffold-lite-infrastructure` | 模型、文档解析、向量存储等适配 |
| `ai-agent-scaffold-lite-app` | 启动、配置和数据库迁移 |
| `ai-agent-scaffold-lite-types` | 公共类型 |
| `ai-agent-scaffold-lite-web` | 动态团队运行控制台 |

## 本地运行

准备 JDK 17、Maven 3.6.3 或以上版本、PostgreSQL/pgvector 和所需模型服务。根目录 `.env` 可提供 `RAG_DB_URL`、`RAG_DB_USERNAME`、`RAG_DB_PASSWORD`、`DeepSeek_API`、`ALiYun_API` 等配置。不要提交密钥；真实模型调用可能产生费用。

开发环境默认读取 `agent/only-one-agent.yml` 作为基础装配验证配置。后端可通过 IDEA 运行 `ai-agent-scaffold-lite-app` 的 `Application`。启动时 Flyway 会执行数据库迁移；**V8 会删除旧画布的 `published_workflow` 表及其中数据**。已有数据库需要先确认是否保存旧配置，再启动使用此迁移的应用。

启动后，演示登录页为 `http://localhost:8091/login.html`，动态团队控制台为 `http://localhost:8091/team/`。旧的 `/canvas/?view=dynamic-team` 仅保留跳转入口。演示登录只检查浏览器 Cookie，不提供服务端鉴权。

修改前端时，在 `ai-agent-scaffold-lite-web` 目录执行：

```powershell
npm ci
npm run dev
npm run build
```

构建输出位于 `docs/dev-ops/nginx/html/team/`；Maven 构建会将其复制到应用静态资源。请修改前端源代码，不要直接编辑构建产物。

### 动态团队默认限制

以 `application.yml` 为准：最多 5 个 Worker、20 个任务、3 个并发 Worker，单步时限 600 秒、整次运行时限 1200 秒，最多 25 次应用层逻辑模型调用，用户输入最多 20,000 字符。`max-replans=2` 是预留配置，当前不执行重新规划。上述值可通过对应的 `DYNAMIC_TEAM_*` 环境变量覆盖。

## 接口

| 路径 | 用途 |
|---|---|
| `GET /api/v1/dynamic-team/capabilities` | 查询服务端运行限制 |
| `POST /api/v1/dynamic-team/runs` | 异步创建运行 |
| `GET /api/v1/dynamic-team/runs/{runId}` | 查询运行快照 |
| `GET /api/v1/dynamic-team/runs/{runId}/events/stream` | 回放并订阅 SSE 事件 |
| `POST /api/v1/dynamic-team/runs/{runId}/cancel` | 取消运行 |
| `GET /api/v1/dynamic-team/runs/{runId}/result` | 查询结果 |

开发验证入口：`GET /api/v1/query_ai_agent_config_list`、`POST /api/v1/create_session`、`POST /api/v1/chat`、`POST /api/v1/rag/documents/{documentId}/index`、`POST /api/v1/rag/search`。普通接口使用 `Response<T>` 的 `code`、`info`、`data` 结构。

## 测试与边界

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

前端构建执行 TypeScript 检查。真实外部联调需要显式启用，并准备模型和数据库，可能产生费用。

- 运行记录、事件和普通对话会话保存在单进程内，不支持重启恢复或多实例共享。
- 演示登录不提供可信身份；文档接口和动态团队接口尚无生产级用户隔离与鉴权。
- 模型调用额度按应用层逻辑调用计数，不等于供应商请求数或 Token 消耗。
- 真实模型全链路仅有阶段 0 的 5 个冒烟用例数据（全部完成，平均约 205 秒、6.4 次模型调用），样本小且质量为人工判断，不能作为效果结论；详见 `docs/reports/stage-0-execution-log.md` 和 `docs/reports/stage-0-failure-analysis.md`。Token 数据来自模型供应商控制台，系统内尚无 Trace 统计。