import type { CanvasDocument } from './protocol';

export interface ValidationIssue {
  code: string;
  message: string;
  nodeId?: string;
  edgeId?: string;
}

export interface ValidationResult {
  issues: ValidationIssue[];
  orderedNodeIds: string[] | null;
}

const identifier = /^[A-Za-z_][A-Za-z0-9_]*$/;
const references = /\{([A-Za-z_][A-Za-z0-9_]*)\}/g;

/** Checks editable Agent fields independently of the workflow topology. */
export function validateAgentConfiguration(document: CanvasDocument): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const names = new Map<string, string>();
  const outputs = new Map<string, string>();

  if (document.nodes.length === 0) {
    issues.push({ code: 'NO_AGENTS', message: '画布中还没有 Agent，请先添加节点。' });
  }

  for (const node of document.nodes) {
    const { name, instruction, outputKey } = node.config;
    const agentName = name.trim();
    const key = outputKey?.trim();

    if (!agentName) {
      issues.push({ code: 'AGENT_NAME_REQUIRED', nodeId: node.id, message: '请填写 Agent 内部名称。' });
    } else if (!identifier.test(agentName)) {
      issues.push({ code: 'AGENT_NAME_INVALID', nodeId: node.id, message: `Agent“${agentName}”的内部名称只能由英文字母、数字和下划线组成，且不能以数字开头。` });
    } else if (names.has(agentName)) {
      issues.push({ code: 'AGENT_NAME_DUPLICATE', nodeId: node.id, message: `Agent 内部名称“${agentName}”重复，请修改其中一个。` });
    } else {
      names.set(agentName, node.id);
    }

    if (!instruction.trim()) {
      issues.push({ code: 'INSTRUCTION_REQUIRED', nodeId: node.id, message: `Agent“${agentName || node.id}”缺少提示词。` });
    }

    if (outputKey !== undefined && outputKey !== '' && !key) {
      issues.push({ code: 'OUTPUT_KEY_INVALID', nodeId: node.id, message: `Agent“${agentName || node.id}”的输出变量不能只填空格。` });
    } else if (key && !identifier.test(key)) {
      issues.push({ code: 'OUTPUT_KEY_INVALID', nodeId: node.id, message: `输出变量“${key}”只能由英文字母、数字和下划线组成，且不能以数字开头。` });
    } else if (key && outputs.has(key)) {
      issues.push({ code: 'OUTPUT_KEY_DUPLICATE', nodeId: node.id, message: `输出变量“${key}”重复，请为每个 Agent 使用不同的名称。` });
    } else if (key) {
      outputs.set(key, node.id);
    }
  }

  for (const node of document.nodes) {
    const seen = new Set<string>();
    for (const match of node.config.instruction.matchAll(references)) {
      const key = match[1];
      if (seen.has(key)) continue;
      seen.add(key);
      if (!outputs.has(key)) {
        issues.push({
          code: 'OUTPUT_REFERENCE_MISSING',
          nodeId: node.id,
          message: `提示词引用了“{${key}}”，但画布中没有 Agent 配置这个输出变量。`,
        });
      }
    }
  }

  return issues;
}

/** V1 sequential semantics. Parallel and loop workflows need their own topology validator. */
export function validateSequentialWorkflow(document: CanvasDocument): ValidationResult {
  const issues = validateAgentConfiguration(document);
  const nodesById = new Map(document.nodes.map((node) => [node.id, node]));
  const incoming = new Map(document.nodes.map((node) => [node.id, [] as CanvasDocument['edges']]));
  const outgoing = new Map(document.nodes.map((node) => [node.id, [] as CanvasDocument['edges']]));

  for (const edge of document.edges) {
    if (!nodesById.has(edge.source) || !nodesById.has(edge.target)) {
      issues.push({ code: 'EDGE_ENDPOINT_MISSING', edgeId: edge.id, message: '连线指向不存在的 Agent，请删除这条连线。' });
      continue;
    }
    outgoing.get(edge.source)!.push(edge);
    incoming.get(edge.target)!.push(edge);
  }

  if (document.nodes.length === 0) return { issues, orderedNodeIds: null };

  for (const node of document.nodes) {
    if (incoming.get(node.id)!.length > 1) {
      issues.push({ code: 'MULTIPLE_PREDECESSORS', nodeId: node.id, message: `Agent“${node.config.name || node.id}”有多个前驱；顺序工作流中只能有一个。` });
    }
    if (outgoing.get(node.id)!.length > 1) {
      issues.push({ code: 'MULTIPLE_SUCCESSORS', nodeId: node.id, message: `Agent“${node.config.name || node.id}”有多个后继；顺序工作流中只能有一个。` });
    }
  }

  const starts = document.nodes.filter((node) => incoming.get(node.id)!.length === 0);
  const ends = document.nodes.filter((node) => outgoing.get(node.id)!.length === 0);
  if (starts.length !== 1) {
    issues.push({ code: 'START_COUNT', message: `顺序工作流需要一个起点，当前找到 ${starts.length} 个。请连接独立节点并检查环形连线。` });
  }
  if (ends.length !== 1) {
    issues.push({ code: 'END_COUNT', message: `顺序工作流需要一个终点，当前找到 ${ends.length} 个。请连接独立节点并检查环形连线。` });
  }

  const visiting = new Set<string>();
  const visited = new Set<string>();
  const reportedCycles = new Set<string>();
  const visit = (nodeId: string) => {
    visiting.add(nodeId);
    for (const edge of outgoing.get(nodeId)!) {
      if (visiting.has(edge.target)) {
        if (!reportedCycles.has(edge.id)) {
          issues.push({ code: 'CYCLE', edgeId: edge.id, message: '这条连线形成了环；顺序工作流不能循环执行。' });
          reportedCycles.add(edge.id);
        }
      } else if (!visited.has(edge.target)) {
        visit(edge.target);
      }
    }
    visiting.delete(nodeId);
    visited.add(nodeId);
  };
  for (const node of document.nodes) {
    if (!visited.has(node.id)) visit(node.id);
  }

  const reachable = new Set<string>();
  const pending = [starts[0]?.id ?? document.nodes[0].id];
  while (pending.length > 0) {
    const nodeId = pending.pop()!;
    if (reachable.has(nodeId)) continue;
    reachable.add(nodeId);
    for (const edge of outgoing.get(nodeId)!) pending.push(edge.target);
  }
  for (const node of document.nodes) {
    if (!reachable.has(node.id)) {
      issues.push({ code: 'DISCONNECTED_NODE', nodeId: node.id, message: `Agent“${node.config.name || node.id}”不在起点到终点的执行链上，请检查连线。` });
    }
  }

  let orderedNodeIds: string[] | null = null;
  if (starts.length === 1) {
    const order: string[] = [];
    const seen = new Set<string>();
    let current: string | undefined = starts[0].id;
    while (current && !seen.has(current)) {
      order.push(current);
      seen.add(current);
      current = outgoing.get(current)![0]?.target;
    }
    if (issues.length === 0) orderedNodeIds = order;
  }

  if (orderedNodeIds) {
    const produced = new Set<string>();
    for (const nodeId of orderedNodeIds) {
      const node = nodesById.get(nodeId)!;
      const seenReferences = new Set<string>();
      for (const match of node.config.instruction.matchAll(references)) {
        const key = match[1];
        if (seenReferences.has(key)) continue;
        seenReferences.add(key);
        if (!produced.has(key)) {
          issues.push({ code: 'OUTPUT_REFERENCE_NOT_UPSTREAM', nodeId, message: `Agent“${node.config.name}”引用了“{${key}}”，但该变量不是前面 Agent 的输出。` });
        }
      }
      if (node.config.outputKey?.trim()) produced.add(node.config.outputKey.trim());
    }
    if (issues.length > 0) orderedNodeIds = null;
  }

  return { issues, orderedNodeIds };
}
