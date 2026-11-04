# Agent Canvas 前端

独立的 React + TypeScript + React Flow 工程，实现开发计划中的最小画布编辑器。

```powershell
cd ai-agent-scaffold-lite-web
npm ci
npm run dev
```

本地开发地址为 `http://localhost:5173/canvas/`。开发服务器不检查演示登录 Cookie，正式构建的页面沿用现有登录页的 Cookie 检查。

提交或运行 Java 应用前执行：

```powershell
npm run build
```

构建产物输出到 `docs/dev-ops/nginx/html/canvas/`。现有 Nginx 直接服务该目录；`ai-agent-scaffold-lite-app/pom.xml` 会将 `canvas/**` 复制进 Spring Boot 的 `static` 目录。页面入口是 `/canvas/index.html`。不要直接修改构建产物，应修改 `src/` 后重新构建。

当前实现覆盖节点添加、移动、属性编辑、连接和删除；支持多份本地工作流草稿的自动保存、切换、JSON 导入和导出。点击“校验顺序工作流”可检查 Agent 配置、输出引用和顺序连线，并定位错误节点或连线。JSON 草稿允许未完成的工作流；YAML 转换和后端热装配按开发计划后续节点实现。

校验器测试：`npm test`。
