# Workflow Canvas API Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让画布预览接口遵循 API 契约、DTO 和统一 `Response<DTO>` 返回规范。

**Architecture:** API 层定义请求、响应和接口；Trigger 层负责 API DTO 与 Domain VO 的映射；Domain 校验和转换逻辑保持不变。前端解包统一响应后继续处理校验问题和 YAML。

**Tech Stack:** Java 17、Spring MVC、Lombok、React、TypeScript

**Spec:** `docs/agent-canvas-v1.md`

## Global Constraints

- Domain 服务保留接口与实现分离。
- HTTP 契约放在 API 层，并使用 `Response<DTO>`。
- 不把 Domain VO 暴露为 HTTP 请求或响应。
- 当前工作区包含用户未提交改动，不创建提交、不覆盖无关文件。

## Review Focus

- 校验失败仍返回问题列表供前端定位。
- 成功响应包含执行顺序和 YAML。
- 响应不再暴露 `AiAgentConfigTableVO`。
- API DTO 能完整接收画布 JSON。
- 前端能正确处理统一响应的成功与业务失败状态。

---

### Task 1: API 契约与 Controller

**Files:**
- Create: `ai-agent-scaffold-lite-api/src/main/java/cn/bugstack/ai/api/IWorkflowCanvasApi.java`
- Create: `ai-agent-scaffold-lite-api/src/main/java/cn/bugstack/ai/api/dto/WorkflowCanvasRequestDTO.java`
- Create: `ai-agent-scaffold-lite-api/src/main/java/cn/bugstack/ai/api/dto/WorkflowCanvasPreviewResponseDTO.java`
- Create: `ai-agent-scaffold-lite-api/src/main/java/cn/bugstack/ai/api/dto/CanvasValidationIssueDTO.java`
- Modify: `ai-agent-scaffold-lite-trigger/src/main/java/cn/bugstack/ai/trigger/http/canvas/WorkflowCanvasController.java`
- Test: `ai-agent-scaffold-lite-trigger/src/test/java/cn/bugstack/ai/test/trigger/WorkflowCanvasControllerTest.java`

**Interfaces:**
- Produces: `Response<WorkflowCanvasPreviewResponseDTO> preview(WorkflowCanvasRequestDTO requestDTO)`

- [x] 先修改 Controller 测试，断言统一响应字段及不返回 `config`。
- [x] 运行测试并确认因新契约尚未实现而失败。
- [x] 新增 API 接口和 DTO，完成 Controller 映射与统一响应。
- [x] 运行 Controller 测试并确认通过。

### Task 2: 前端解包与编码规范

**Files:**
- Modify: `ai-agent-scaffold-lite-web/src/preview.ts`
- Create: `docs/编码规范.md`

**Interfaces:**
- Consumes: `Response<WorkflowCanvasPreviewResponseDTO>`

- [x] 修改前端请求测试，断言统一响应解包和业务失败结果。
- [x] 运行测试并确认旧实现失败。
- [x] 修改 `preview.ts` 解包 `data`，异常响应显示 `info`。
- [x] 新建精简的 `docs/编码规范.md`。
- [x] 运行前端测试和构建，并运行后端相关测试。
