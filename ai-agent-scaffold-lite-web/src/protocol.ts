import { MarkerType, type Edge, type Node, type Viewport } from '@xyflow/react';

export interface AgentConfig {
  name: string;
  description: string;
  instruction: string;
  outputKey?: string;
}

export type AgentNodeData = { config: AgentConfig } & Record<string, unknown>;
export type AgentFlowNode = Node<AgentNodeData, 'agent'>;
export type AgentFlowEdge = Edge;

export interface CanvasDocument {
  schemaVersion: '2.0';
  workflow: { id: string; name: string; description: string };
  module: {
    aiApi: { baseUrl: string; apiKey: string; completionsPath: string; embeddingsPath: string };
    chatModel: { model: string };
  };
  editor: { viewport: Viewport };
  nodes: Array<{
    id: string;
    type: 'agent';
    position: { x: number; y: number };
    config: AgentConfig;
  }>;
  edges: Array<{ id: string; source: string; target: string }>;
}

export function toCanvasDocument(
  workflow: CanvasDocument['workflow'],
  module: CanvasDocument['module'],
  viewport: Viewport,
  nodes: AgentFlowNode[],
  edges: AgentFlowEdge[],
): CanvasDocument {
  return {
    schemaVersion: '2.0',
    workflow,
    module,
    editor: { viewport },
    nodes: nodes.map(({ id, position, data }) => ({ id, type: 'agent', position, config: { ...data.config } })),
    edges: edges.map(({ id, source, target }) => ({ id, source, target })),
  };
}

export function fromCanvasDocument(document: CanvasDocument): {
  nodes: AgentFlowNode[];
  edges: AgentFlowEdge[];
  viewport: Viewport;
} {
  return {
    nodes: document.nodes.map(({ id, position, config }) => ({
      id,
      type: 'agent',
      position: { ...position },
      data: { config: { ...config } },
    })),
    edges: document.edges.map(({ id, source, target }) => ({
      id,
      source,
      target,
      markerEnd: { type: MarkerType.ArrowClosed },
    })),
    viewport: { ...document.editor.viewport },
  };
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function nonEmpty(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function environmentPlaceholder(value: unknown): value is string {
  return typeof value === 'string' && /^\$\{[A-Za-z_][A-Za-z0-9_]*(?::[^}]*)?\}$/.test(value);
}

function point(value: unknown): value is { x: number; y: number } {
  return record(value) && Number.isFinite(value.x) && Number.isFinite(value.y);
}

export function parseCanvasDocument(value: unknown): CanvasDocument {
  if (!record(value)) throw new Error('文件内容不是画布 JSON 对象。');
  if (value.schemaVersion !== '2.0') {
    throw new Error(`不支持的数据协议版本：${String(value.schemaVersion ?? '缺失')}。当前仅支持 2.0。`);
  }
  const { workflow, editor, nodes, edges } = value;
  if (!record(workflow) || !nonEmpty(workflow.id) || !nonEmpty(workflow.name) || typeof workflow.description !== 'string') {
    throw new Error('工作流 ID、名称或描述格式错误。');
  }
  const module = value.module;
  if (!record(module) || !record(module.aiApi) || !record(module.chatModel) ||
      !nonEmpty(module.aiApi.baseUrl) || !environmentPlaceholder(module.aiApi.apiKey) ||
      !nonEmpty(module.aiApi.completionsPath) || !nonEmpty(module.aiApi.embeddingsPath) ||
      !nonEmpty(module.chatModel.model)) {
    throw new Error('公共 API 或模型配置格式错误；密钥只能使用环境变量占位符。');
  }
  if (!record(editor) || !point(editor.viewport) || !('zoom' in editor.viewport) ||
      !Number.isFinite(editor.viewport.zoom) || (editor.viewport.zoom as number) <= 0) {
    throw new Error('画布视口格式错误。');
  }
  if (!Array.isArray(nodes) || !Array.isArray(edges)) throw new Error('节点或连线不是数组。');
  const nodeIds = new Set<string>();
  for (const node of nodes) {
    if (!record(node) || !nonEmpty(node.id) || node.type !== 'agent' || !point(node.position) || !record(node.config) ||
        typeof node.config.name !== 'string' || typeof node.config.description !== 'string' ||
        typeof node.config.instruction !== 'string' ||
        (node.config.outputKey !== undefined && typeof node.config.outputKey !== 'string')) {
      throw new Error('Agent 节点字段格式错误。');
    }
    if (nodeIds.has(node.id)) throw new Error(`节点 ID 重复：${node.id}`);
    nodeIds.add(node.id);
  }
  const edgeIds = new Set<string>();
  for (const edge of edges) {
    if (!record(edge) || !nonEmpty(edge.id) || !nonEmpty(edge.source) || !nonEmpty(edge.target)) {
      throw new Error('连线字段格式错误。');
    }
    if (edgeIds.has(edge.id)) throw new Error(`连线 ID 重复：${edge.id}`);
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) throw new Error(`连线 ${edge.id} 指向不存在的节点。`);
    edgeIds.add(edge.id);
  }
  return value as unknown as CanvasDocument;
}
