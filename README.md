# xfg-frame-archetype - DDD 脚手架 - @小傅哥 v2.2

- docker 使用文档：[https://bugstack.cn/md/road-map/docker.html](https://bugstack.cn/md/road-map/docker.html)
- DDD 教程；
  - [DDD 概念理论](https://bugstack.cn/md/road-map/ddd-guide-01.html)
  - [DDD 建模方法](https://bugstack.cn/md/road-map/ddd-guide-02.html)
  - [DDD 工程模型](https://bugstack.cn/md/road-map/ddd-guide-03.html)
  - [DDD 架构设计](https://bugstack.cn/md/road-map/ddd.html)
  - [DDD 建模案例](https://bugstack.cn/md/road-map/ddd-model.html)

## 本机 IDEA 启动

在 IDEA 顶部选择 **AI Agent Scaffold Lite**，点击运行三角标。该配置使用项目根目录读取
`.env`，启用 `dev` 环境，并在编译和启动之前自动执行 SSH 隧道检查。运行配置位于
`.idea/runConfigurations/AI_Agent_Scaffold_Lite.xml`（本机配置，不提交 Git）。

- 使用 JDK 17；运行内存设为 `-Xms128m -Xmx768m`。
- 隧道使用 `%USERPROFILE%\.ssh\ai_agent_scaffold_pgvector`，把本机
  `127.0.0.1:15432` 转发到数据库服务器。已有对应隧道时直接复用，失败则阻止应用启动。
- 前端登录地址为 `http://localhost:8091/login.html`，演示账号/密码为 `admin / admin`。
  页面、JS 和图片由 Java 应用直接提供，无需启动 Nginx 或使用 IDEA 的 `63342` 预览端口。
  Maven / IDEA 构建会将 `docs/dev-ops/nginx/html` 中的前端资源复制到应用的 `static` 目录。
  可访问
  `http://localhost:8091/api/v1/query_ai_agent_config_list` 检查服务。
- 停止 Java 应用后，隧道保留供下次启动复用；电脑重启后，运行前任务会重新建立隧道。
- 隧道错误日志位于 `data/dev/pgvector-tunnel.stderr.log`。不要提交 `.env` 或 SSH 私钥。

也可以在项目根目录单独准备隧道：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/dev/start-pgvector-tunnel.ps1
```

完整 Maven 打包请使用 IDEA 自带的 Maven 3.9 或其他 Maven 3.6.3 及以上版本；
本机系统 Maven 3.5 无法执行当前依赖的打包插件。

## RAG 问答

项目已提供完整的“文档解析 → 清洗 → 切分 → 向量化 → pgvector 入库 → 相似度检索 → 大模型生成答案”链路。

### 接口

- `POST /api/v1/rag/documents/{documentId}/index`：以 `multipart/form-data` 上传并重建文档索引，表单字段为 `file` 和 `userId`。前端会自动生成文档 ID。
- `POST /api/v1/rag/search`：仅返回相似 Chunk。
- `POST /api/v1/rag/answer`：自动检索相关 Chunk、拼入提示词并调用大模型生成答案，同时返回实际使用的 `sources`。

登录后，在对话页左下角点击“上传文档”，选择文件并等待入库完成，再返回原聊天页提问。聊天服务会先由模型判断是否需要查询知识库；需要时只检索当前 `userId` 上传的文档，在本轮回答中附带 `[S1]` 等来源编号。旧版接口入库、未记录 `userId` 的文档不会出现在自动检索结果中，需重新上传。当前登录页是演示登录，生产环境还需要服务端身份认证。

问答请求示例：

```json
{
  "query": "未单独列明城市时，普通员工住宿费用上限是多少？",
  "topK": 5,
  "minSimilarity": 0.0,
  "documentIds": ["employee-expense-guide"]
}
```

### 配置

本地密钥和数据库连接放在根目录 `.env`，该文件已被 Git 忽略。主要环境变量如下：

- `ALiYun_API`：Embedding 与对话模型密钥。
- `RAG_DB_URL`、`RAG_DB_USERNAME`、`RAG_DB_PASSWORD`：PostgreSQL + pgvector 连接。
- `AI_CHAT_BASE_URL`、`AI_CHAT_COMPLETIONS_PATH`、`AI_CHAT_MODEL`：问答模型覆盖配置。
- `RAG_MAX_CONTEXT_CHARACTERS`：单次提示词允许拼入的检索正文最大字符数，默认 `12000`。
- `RAG_ROUTER_ENABLED`：是否启用聊天前的模型路由，默认 `true`。
- `RAG_CHAT_MIN_SIMILARITY`：自动聊天检索的最低相似度，默认 `0.35`。
- `RAG_MAX_FILE_SIZE`、`RAG_MAX_REQUEST_SIZE`：上传大小限制，默认 `20MB`、`25MB`。

真实 HTTP 端到端测试默认不会消耗外部额度；需要联调时显式传入 `-Drag.answer.live-test=true` 运行 `RagAnswerEndToEndLiveTest`。

## 自动化测试

项目通过 Maven Wrapper 固定构建入口，并将快速测试、容器集成测试、真实外部服务联调和示例代码分开管理。

```powershell
# 日常开发
.\mvnw.cmd test

# 提交前完整校验：单元测试、集成测试、JaCoCo 报告
.\mvnw.cmd verify
```

测试分层、命名和外部联调方式见 [测试指南](docs/testing.md)。

