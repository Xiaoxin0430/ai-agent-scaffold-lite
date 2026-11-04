import type { CanvasDocument } from './protocol';
import type { ValidationIssue } from './validation';

declare global {
  interface Window {
    APP_CONFIG?: { API_BASE?: string };
  }
}

export interface WorkflowPublishResponse {
  published: boolean;
  workflowId: string | null;
  issues: ValidationIssue[];
}

interface ApiResponse<T> {
  code: string;
  info: string;
  data?: T;
}

export async function requestWorkflowPublish(document: CanvasDocument): Promise<WorkflowPublishResponse> {
  const apiBase = window.APP_CONFIG?.API_BASE || 'http://127.0.0.1:8091';
  const response = await fetch(`${apiBase}/api/v1/canvas/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(document),
  });

  let result: ApiResponse<WorkflowPublishResponse>;
  try {
    result = await response.json() as ApiResponse<WorkflowPublishResponse>;
  } catch {
    throw new Error(`后端返回了无法解析的响应（HTTP ${response.status}）。`);
  }

  if (!response.ok || (result.code !== '0000' && result.code !== '0002')) {
    throw new Error(result.info || `工作流发布失败（HTTP ${response.status}）。`);
  }
  if (!result.data) {
    throw new Error(result.info || '后端发布响应缺少数据。');
  }
  return result.data;
}
