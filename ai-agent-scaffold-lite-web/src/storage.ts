import { parseCanvasDocument, type CanvasDocument } from './protocol';

export interface CanvasWorkspace {
  activeWorkflowId: string;
  drafts: Record<string, CanvasDocument>;
}

function storageKey(): string {
  const cookie = document.cookie.split('; ').find((item) => item.startsWith('ai_agent_login='));
  let user = 'local';
  if (cookie) {
    try {
      const payload = JSON.parse(decodeURIComponent(cookie.slice(cookie.indexOf('=') + 1)));
      if (typeof payload?.user === 'string' && payload.user) user = payload.user;
    } catch { /* A malformed demo login cookie uses the local namespace. */ }
  }
  return `agent-canvas:workspace:${encodeURIComponent(user)}`;
}

export function createBlankDocument(): CanvasDocument {
  return {
    schemaVersion: '2.0',
    workflow: { id: `wf_${crypto.randomUUID()}`, name: 'NewWorkflow', description: '' },
    module: {
      aiApi: {
        baseUrl: 'https://api.deepseek.com',
        apiKey: '${DeepSeek_API:}',
        completionsPath: '/chat/completions',
        embeddingsPath: '/embeddings',
      },
      chatModel: { model: 'deepseek-flash' },
    },
    editor: { viewport: { x: 0, y: 0, zoom: 1 } },
    nodes: [],
    edges: [],
  };
}

export function loadWorkspace(): CanvasWorkspace | null {
  const raw = localStorage.getItem(storageKey());
  if (raw === null) return null;
  const value: unknown = JSON.parse(raw);
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error('本地草稿索引格式错误。');
  const saved = value as Record<string, unknown>;
  if (typeof saved.activeWorkflowId !== 'string' || typeof saved.drafts !== 'object' ||
      saved.drafts === null || Array.isArray(saved.drafts)) throw new Error('本地草稿索引格式错误。');
  const drafts: Record<string, CanvasDocument> = {};
  for (const [id, draft] of Object.entries(saved.drafts)) {
    const parsed = parseCanvasDocument(draft);
    if (id !== parsed.workflow.id) throw new Error(`草稿索引与工作流 ID 不一致：${id}`);
    drafts[id] = parsed;
  }
  if (!drafts[saved.activeWorkflowId]) throw new Error('当前工作流草稿不存在。');
  return { activeWorkflowId: saved.activeWorkflowId, drafts };
}

export function saveWorkspace(workspace: CanvasWorkspace): void {
  localStorage.setItem(storageKey(), JSON.stringify(workspace));
}
