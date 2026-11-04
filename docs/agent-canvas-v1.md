# Agent Canvas V1：面试前复习

> 本文以当前代码为准，记录画布 V1 的功能、关键实现和设计取舍。画布核心开发已完成；“代码已实现”与“真实环境端到端验收完成”分别表述。文中的 YAML 预览、真实模型对话及远端数据库重启联调边界见最后一节。

## 1. 先记住这张全景图

```text
画布编辑（React Flow）
  → 本地草稿 / JSON 导入导出
  → 前端即时校验
  → POST /api/v1/canvas/publish
  → 后端校验并转换为 AiAgentConfigTableVO
  → PostgreSQL 保存带 API Key 占位符的配置
  → 创建运行时副本，解析 API Key
  → Armory 装配 Agent、顺序工作流、InMemoryRunner
  → 将 AiAgentRegisterVO 注册为 Spring Bean
  → 对话页查询 Bean、创建会话、执行对话

应用重启：YAML 内置配置 + PostgreSQL 已发布配置
  → AiAgentConfigTableVO → Armory 重新装配 → Spring Bean
```

**四种对象要分清：**

| 对象 | 保存位置 | 用途 |
|---|---|---|
| `CanvasDocument` | 浏览器 `localStorage` 或导出的 JSON | 可继续编辑，含节点坐标、视口和连线 |
| `AiAgentConfigTableVO` 原始配置 | PostgreSQL `published_workflow.config_json` | 已发布配置，API Key 保留环境变量占位符 |
| 运行时配置副本 | 当前发布或启动恢复流程中 | 将占位符解析为真实密钥，交给 Armory |
| `AiAgentRegisterVO` | 当前进程的 Spring 容器 | 持有元数据和 `InMemoryRunner`，供列表与对话使用 |

草稿保存、配置落库和运行时注册是不同步骤。数据库恢复的是工作流配置，不恢复历史会话。

## 2. V1 的范围和协议

V1 只允许一条顺序执行链，例如 `requirement_analyzer → solution_writer → quality_reviewer`。节点是 Agent，共用一套 API 和模型配置。画布不支持并行、循环、条件分支、MCP 或 Skill 的可视化装配，也没有画布内调试、发布版本或会话持久化。

前后端画布协议使用 `schemaVersion: "2.0"`。核心结构如下，省略的 `position`、`editor.viewport` 只用于编辑器恢复：

```json
{
  "schemaVersion": "2.0",
  "workflow": { "id": "wf_requirement_pipeline", "name": "需求处理", "description": "顺序处理需求" },
  "module": {
    "aiApi": {
      "baseUrl": "https://api.deepseek.com",
      "apiKey": "${DeepSeek_API:}",
      "completionsPath": "/chat/completions",
      "embeddingsPath": "/embeddings"
    },
    "chatModel": { "model": "deepseek-flash" }
  },
  "editor": { "viewport": { "x": 0, "y": 0, "zoom": 1 } },
  "nodes": [
    { "id": "n1", "type": "agent", "position": { "x": 0, "y": 0 },
      "config": { "name": "requirement_analyzer", "description": "分析需求", "instruction": "分析需求", "outputKey": "requirement_result" } },
    { "id": "n2", "type": "agent", "position": { "x": 300, "y": 0 },
      "config": { "name": "solution_writer", "description": "编写方案", "instruction": "根据 {requirement_result} 编写方案" } }
  ],
  "edges": [{ "id": "e1", "source": "n1", "target": "n2" }]
}
```

- `workflow.id` 是稳定业务 ID，同时用作 Spring Bean 名称和对话接口的 `agentId`；新草稿由前端生成 `wf_${crypto.randomUUID()}`。
- `nodes[].id` 是编辑器节点 ID；`config.name` 是 ADK Agent 内部名称。两者用途不同。
- `edges` 决定执行顺序，节点数组的原始顺序不决定执行顺序。
- `outputKey` 产生共享变量，后继 Agent 在提示词中用 `{outputKey}` 引用上游输出。
- 画布和数据库保留 API Key 占位符；真实密钥由后端运行时解析。协议中的 `model` 字段沿用项目现有配置。

## 3. 从编辑到校验

### 3.1 前端编辑和草稿

`ai-agent-scaffold-lite-web/src/App.tsx` 使用 React Flow 添加、移动、删除 Agent 和有向连线，编辑工作流与 Agent 属性。`storage.ts` 将多份草稿自动保存到浏览器 `localStorage`，支持切换和删除；`file.ts` 负责 JSON 导入、导出。导入时由 `protocol.ts` 检查结构。草稿允许未连接或未填写完整，发布时才要求通过业务校验。

草稿按演示登录 Cookie 中的用户名划分浏览器存储命名空间。这不是后端认证或可信的用户隔离。页面发布成功后显示提示；对话页需要自行查询工作流列表。

### 3.2 两层校验

前端 `validation.ts` 即时提示问题。后端 `SequentialWorkflowCanvasValidator` 独立校验，不能信任客户端传来的校验结果。后端检查：

1. 协议版本、工作流 ID 和元数据、公共 API/模型、视口和 API Key 占位符。
2. Agent ID、内部名称、提示词、输出变量的合法性与唯一性。
3. 连线端点、单个前驱/后继、唯一首尾节点、环和孤立节点。
4. `{outputKey}` 是否存在，且是否由执行链上更早的 Agent 产生。
5. 自动生成的内部工作流名称是否与 Agent 名称冲突。

校验成功会返回唯一的 `orderedNodeIds`。不通过时返回带 `code`、`message`、`nodeId` 或 `edgeId` 的问题列表，Controller 转成 API DTO，供前端定位。`WorkflowCanvasService.validateAndConvert` 只在校验通过后调用转换器。

### 3.3 转换为 Armory 配置

`WorkflowCanvasConverter` 根据 `orderedNodeIds` 组装 `AiAgentConfigTableVO`：

```text
workflow 元数据 → config.workflow
公共 API / 模型 → config.module.aiApi / chatModel
有序 Agent → config.module.agents
Agent 名称列表 → sequential 类型的 agentWorkFlow.subAgents
内部工作流名称 → config.module.runner.agentName
```

内部工作流名由 `workflow.id` 中的 `-` 替换成 `_` 后追加 `_seq`。节点坐标、视口和连线不会进入运行配置；连线的作用是在转换前确定执行顺序。

## 4. 发布：事务、装配与注册

`WorkflowCanvasController` 实现 API 层 `IWorkflowCanvas`，接收 `POST /api/v1/canvas/publish`，把请求 DTO 转为领域对象，并返回项目统一的 `Response<WorkflowCanvasPublishResponseDTO>`。无效画布返回问题列表；业务冲突和装配异常返回相应错误。

`WorkflowPublishService.publish` 是核心业务入口，实际顺序如下：

```text
后端校验并转换
→ 检查 Spring 容器中是否已有同名工作流
→ WorkflowRuntimeConfigFactory 创建运行时副本并解析密钥
→ IPublishedWorkflowConfigPort.insert(原始配置)
→ ArmoryService.acceptArmoryAgents(运行时副本)
→ 读取并检查注册的 AiAgentRegisterVO：ID 正确且 Runner 非空
→ 事务提交后，Controller 返回发布成功
```

**为什么有两份配置？** `WorkflowRuntimeConfigFactory` 用 Jackson 复制配置，通过 Spring `Environment.resolvePlaceholders` 解析副本中的 API Key。数据库写入原始配置，避免把真实密钥写进 `JSONB`。环境变量未配置或占位符格式不符合要求时，发布失败。Spring 的 `Environment` 从应用配置和环境属性源取值；单独存在的 `.env` 文件只有被启动方式加载进环境后才会生效。

**数据库如何写入？** Domain 层的 `IPublishedWorkflowConfigPort` 定义 `insert/findAll`；Infrastructure 层的 `PostgresPublishedWorkflowConfigPort` 用 Jackson 序列化配置，再调用 Spring JDBC 的 `JdbcTemplate.update`。SQL 的 `?` 由 JDBC 绑定参数，`?::jsonb` 是 PostgreSQL 的 JSONB 类型转换。Flyway 的 `V7__published_workflow.sql` 建表：`workflow_id` 为主键，`config_json` 为 `JSONB`，另有创建时间。重复 ID 会转为发布冲突；当前没有同 ID 更新功能。

**事务包住哪里？** `publish` 标有 `@Transactional(rollbackFor = Exception.class)`。数据库插入与后续 Armory 装配处在同一次 Spring 事务中。装配或检查抛异常时，事务回滚数据库插入；数据库事务无法自动撤销 Spring 容器中的 Bean，因此还需要运行时清理。

**Armory 做了什么？** `ArmoryService` 把配置交给装配树；主要经过 API、ChatModel、Agent、AgentWorkflow 和 Runner 节点。画布 V1 转出的工作流类型为 `sequential`。`RunnerNode` 构建 `InMemoryRunner` 和 `AiAgentRegisterVO`，随后调用 `IWorkflowRuntimeRegistry.registerIfAbsent(workflowId, registered)`。注册是 Armory 装配过程的最后一段，不是发布服务在装配后另行注册。

**失败如何清理？** `SpringWorkflowRuntimeRegistry` 用 `DefaultListableBeanFactory.registerBeanDefinition` 注册 Bean。`unregisterIfSame` 先确认当前 Bean 与本次注册对象是同一个实例，再调用 Spring 的 `removeBeanDefinition`，避免误删后来注册的同名对象。装配异常走 `catch` 清理；若装配返回后事务最终未提交，`TransactionSynchronization.afterCompletion` 再清理。发布方法上的 `synchronized` 只协调当前服务实例内的发布调用；它不能代替数据库主键或跨实例协调。

**一致性边界：**数据库事务与 Spring Bean 注册不是同一种事务。注册发生在数据库提交前，其他并发查询理论上可能短暂看见尚未提交的工作流；失败后的回调会清理它。当前代码没有实现严格的跨资源原子可见性。

## 5. 重启恢复、列表与对话

应用启动的 `ApplicationReadyEvent` 中，`AiAgentAutoConfig` 先装配 YAML 中的内置配置，再调用仓储的 `findAll()` 读取已发布工作流。仓储校验数据库行的 `workflow_id` 与 JSON 内的 ID 一致。每条数据库配置先创建解析了密钥的运行时副本，再交给相同的 Armory 链路装配、注册。某条恢复失败会抛出异常并中断该恢复流程。

对话页调用 `GET /api/v1/query_ai_agent_config_list`；`ChatService.queryAiAgentConfigList` 从 Spring 容器获取 `AiAgentRegisterVO`，返回工作流 ID、名称和描述。因此列表展示的是成功注册的运行时对象，不是直接查数据库。

用户选择工作流后，创建会话接口按 `agentId` 取得 `AiAgentRegisterVO` 和 Runner。会话映射键是 `(agentId, userId)`，相同用户在不同工作流不会共用同一个会话 ID。发送消息时，现有 `ChatService` 会进行 RAG 路由判断，必要时检索文档并构造上下文，再调用 Runner 执行；网页使用现有流式对话接口。Runner 和会话都在当前进程内，重启后历史会话不会恢复。

## 6. 面试时可以这样解释设计取舍

| 问题 | 简明回答 |
|---|---|
| 为什么后端还要校验？ | 前端校验用于即时提示，请求可绕过前端；后端校验是发布前的权威边界。 |
| 为什么不根据节点数组顺序装配？ | 数组是编辑器数据；连线表达依赖关系，校验器据此产生唯一执行顺序。 |
| 为什么发布不先生成 YAML？ | 转换器已经生成 Armory 所需对象，直接装配可以避免额外序列化和反解析。YAML 是辅助导出格式。 |
| 为什么保存占位符？ | 避免持久化真实 API Key；重启时可从当前环境重新解析。 |
| 为什么既用事务又手动注销 Bean？ | 事务只管理数据库写入，动态注册的 Spring Bean 需要单独补偿。 |
| `unregisterIfSame` 中为什么用 `==`？ | 比较对象身份，只撤销本次发布创建的那个注册对象。 |
| 列表为什么不直接查数据库？ | 数据库表示已发布配置；Bean 表示当前进程中已成功装配、能够取得 Runner 的工作流。 |
| 重启恢复是否等于会话恢复？ | 不等于。恢复的是工作流配置和 Runner；`InMemoryRunner` 的会话历史仍在内存。 |

## 7. 代码索引

| 职责 | 文件 |
|---|---|
| 页面、协议、草稿、导入导出、前端校验与发布 | `ai-agent-scaffold-lite-web/src/{App,protocol,storage,file,validation,publish}.ts(x)` |
| HTTP 契约和入口 | `ai-agent-scaffold-lite-api/.../IWorkflowCanvas.java`、`ai-agent-scaffold-lite-trigger/.../canvas/WorkflowCanvasController.java` |
| 后端校验与转换 | `domain/.../canvas/validation/SequentialWorkflowCanvasValidator.java`、`WorkflowCanvasService.java`、`WorkflowCanvasConverter.java` |
| 发布与密钥副本 | `domain/.../canvas/WorkflowPublishService.java`、`WorkflowRuntimeConfigFactory.java` |
| 数据库端口、实现、建表 | `domain/.../adapter/port/IPublishedWorkflowConfigPort.java`、`infrastructure/.../PostgresPublishedWorkflowConfigPort.java`、`app/src/main/resources/db/migration/V7__published_workflow.sql` |
| 装配、注册与恢复 | `domain/.../armory/ArmoryService.java`、`RunnerNode.java`、`SpringWorkflowRuntimeRegistry.java`、`app/.../AiAgentAutoConfig.java` |
| 列表、会话和对话 | `domain/.../chat/ChatService.java`、`trigger/.../AgentServiceController.java` |

表中 `...` 省略对应模块已有的 Java 包路径；按类名可以在项目中直接定位。

## 8. 已实现范围与验收边界

画布编辑、草稿、JSON 导入导出、双端校验、转换、在线发布、PostgreSQL 配置持久化、运行时注册和启动恢复均有实现代码。已有针对校验、转换、发布事务行为、注册、仓储和启动恢复的定向测试；这证明相应隔离场景，不代表真实浏览器、远端数据库和外部模型组成的整条用户链路已经完成验收。

截至本文整理时，远端数据库的 V7 迁移、真实重启恢复以及“三个 Agent 发布后在对话页完成一次真实模型对话”没有在本次工作中形成完整验收记录。不要在面试中把隔离测试说成生产环境验证。

YAML 方面只有 `WorkflowCanvasYamlWriter` 和结构测试；画布页面没有 YAML 预览、复制或下载。Writer 当前输出 `ai.workflow.config.tables`，而项目实际配置绑定前缀为 `ai.agent.config.tables`，所以其结果不能直接当作可用的项目配置。画布 JSON 导出与 YAML 导出也不是同一能力。

其他明确边界：发布只支持新 ID；草稿存储在浏览器，本地用户名命名空间不是服务端权限控制；没有发布版本、更新、删除、回滚、历史会话持久化或节点级运行观测。面试介绍应以当前代码和已执行验证为准。
