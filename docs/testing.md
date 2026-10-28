# 测试指南

项目将快速、确定性的自动化测试与需要真实基础设施的测试分开执行。

## 测试分层

| 类型 | 命名 | 外部依赖 | 默认执行 |
|---|---|---|---|
| 单元与组件测试 | `*Test.java` | 不访问真实数据库、模型或 MCP | 是 |
| 基础设施集成测试 | `*IT.java` | Testcontainers 临时容器 | 当前无 |
| 真实模型联调 | `*LiveTest.java` | 真实 API Key、数据库或 MCP | 否 |
| 示例程序 | `*Demo.java` | 按示例决定 | 否 |

测试代码跟随被测生产代码放置：领域测试位于 `ai-agent-scaffold-lite-domain/src/test`，基础设施测试位于 `ai-agent-scaffold-lite-infrastructure/src/test`，接口测试位于 `ai-agent-scaffold-lite-trigger/src/test`。Spring Boot 装配、HTTP 端到端测试和演示代码位于 app 模块。

## 常用命令

```bash
# 日常开发：运行快速测试
./mvnw test

# 提交前：运行全部默认测试并生成 JaCoCo 报告
./mvnw verify

# 显式运行真实模型与 MCP 联调测试
./mvnw test -Plive-ai -Drag.embedding.live-test=true -Drag.answer.live-test=true
```

## 编写约定

- 测试必须包含业务断言，不能只打印结果。
- 单元测试不得读取开发环境 `.env`，不得调用真实模型。
- 外部模型测试只断言稳定契约，例如引用、结构化字段和关键事实，不断言完整自然语言文本。
- 异步测试必须设置超时，禁止永久等待。
- 测试数据使用固定输入，避免当前时间、随机数和网络状态造成不稳定结果。
