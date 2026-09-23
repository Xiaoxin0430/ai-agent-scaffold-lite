# xfg-frame-archetype - DDD 脚手架 - @小傅哥 v2.2

- docker 使用文档：[https://bugstack.cn/md/road-map/docker.html](https://bugstack.cn/md/road-map/docker.html)
- DDD 教程；
  - [DDD 概念理论](https://bugstack.cn/md/road-map/ddd-guide-01.html)
  - [DDD 建模方法](https://bugstack.cn/md/road-map/ddd-guide-02.html)
  - [DDD 工程模型](https://bugstack.cn/md/road-map/ddd-guide-03.html)
  - [DDD 架构设计](https://bugstack.cn/md/road-map/ddd.html)
  - [DDD 建模案例](https://bugstack.cn/md/road-map/ddd-model.html)

## RAG 问答

项目已提供完整的“文档解析 → 清洗 → 切分 → 向量化 → pgvector 入库 → 相似度检索 → 大模型生成答案”链路。

### 接口

- `POST /api/v1/rag/documents/{documentId}/index`：以 `multipart/form-data` 上传并重建文档索引，文件字段名为 `file`。
- `POST /api/v1/rag/search`：仅返回相似 Chunk。
- `POST /api/v1/rag/answer`：自动检索相关 Chunk、拼入提示词并调用大模型生成答案，同时返回实际使用的 `sources`。

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

真实 HTTP 端到端测试默认不会消耗外部额度；需要联调时显式传入 `-Drag.answer.live-test=true` 运行 `RagAnswerEndToEndTest`。

