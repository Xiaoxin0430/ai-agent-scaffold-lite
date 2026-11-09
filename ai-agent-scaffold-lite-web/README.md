# 动态 Agent 团队运行控制台

本工程展示一次动态团队运行的 Planner、临时 Worker、任务依赖、状态事件和 Summarizer 结果。前端调用后端的动态团队接口；运行边界和实际状态由服务端决定。

## 本地开发

```powershell
cd ai-agent-scaffold-lite-web
npm ci
npm run dev
```

开发地址为 `http://localhost:5173/team/`。开发模式不检查演示登录 Cookie，默认请求 `http://127.0.0.1:8091` 的后端；生产构建沿用演示登录页的 Cookie 检查，并可通过 `docs/dev-ops/nginx/html/js/config.js` 配置接口地址。后端需单独启动。

## 构建

```powershell
npm run build
```

命令会先运行 TypeScript 检查，再把页面构建到 `docs/dev-ops/nginx/html/team/`。应用的 Maven 构建将 `team/**` 复制为静态资源，入口为 `/team/`。请修改 `src/`，然后重新构建；不要直接编辑生成文件。

旧的 `/canvas/?view=dynamic-team` 只保留跳转到 `/team/` 的入口。顺序工作流画布、草稿、校验和发布页面已移除。
