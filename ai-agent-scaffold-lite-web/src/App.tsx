import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react';
import {
  addEdge,
  Background,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Connection,
  type NodeProps,
} from '@xyflow/react';
import { fromCanvasDocument, parseCanvasDocument, toCanvasDocument, type AgentConfig, type AgentFlowEdge, type AgentFlowNode, type CanvasDocument } from './protocol';
import { createBlankDocument, loadWorkspace, saveWorkspace, type CanvasWorkspace } from './storage';
import { downloadCanvasFile, readCanvasFile } from './file';
import { requestWorkflowPublish } from './publish';
import { validateSequentialWorkflow, type ValidationIssue } from './validation';

function initialState(): { workspace: CanvasWorkspace; error: string | null } {
  try {
    const saved = loadWorkspace();
    if (saved) return { workspace: saved, error: null };
  } catch (error) {
    const blank = createBlankDocument();
    return {
      workspace: { activeWorkflowId: blank.workflow.id, drafts: { [blank.workflow.id]: blank } },
      error: `本地草稿无法读取，原始数据暂未覆盖：${error instanceof Error ? error.message : String(error)}`,
    };
  }
  const blank = createBlankDocument();
  return { workspace: { activeWorkflowId: blank.workflow.id, drafts: { [blank.workflow.id]: blank } }, error: null };
}

const initial = initialState();
const initialDocument = initial.workspace.drafts[initial.workspace.activeWorkflowId];

function AgentNode({ data, selected }: NodeProps<AgentFlowNode>) {
  const config = data.config;
  return (
    <div className={`agent-node${selected ? ' selected' : ''}${data.validationError ? ' validation-error' : ''}`}>
      <Handle type="target" position={Position.Left} className="node-handle" />
      <div className="node-kicker">AGENT</div>
      <strong>{config.name || '未命名 Agent'}</strong>
      <p>{config.description || '点击节点，在右侧编辑属性'}</p>
      {config.outputKey && <span className="node-output">输出 · {config.outputKey}</span>}
      <Handle type="source" position={Position.Right} className="node-handle" />
    </div>
  );
}

const nodeTypes = { agent: AgentNode };

function CanvasEditor() {
  const restored = fromCanvasDocument(initialDocument);
  const [workflow, setWorkflow] = useState(initialDocument.workflow);
  const [module, setModule] = useState(initialDocument.module);
  const [nodes, setNodes, onNodesChange] = useNodesState<AgentFlowNode>(restored.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<AgentFlowEdge>(restored.edges);
  const [viewport, setSavedViewport] = useState(restored.viewport);
  const [drafts, setDrafts] = useState(initial.workspace.drafts);
  const [canPersist, setCanPersist] = useState(!initial.error);
  const workspaceRef = useRef(initial.workspace);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [showValidation, setShowValidation] = useState(false);
  const [backendIssues, setBackendIssues] = useState<ValidationIssue[] | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [notice, setNotice] = useState(initial.error || '从左侧拖入 Agent，或点击“添加 Agent”开始。');
  const { screenToFlowPosition, fitView, setViewport, getViewport } = useReactFlow<AgentFlowNode, AgentFlowEdge>();
  const selectedNode = nodes.find((node) => node.id === selectedNodeId);
  const selectedEdge = edges.find((edge) => edge.id === selectedEdgeId);
  const validation = useMemo(
    () => validateSequentialWorkflow(toCanvasDocument(workflow, module, viewport, nodes, edges)),
    [workflow, module, viewport, nodes, edges],
  );
  const validationIssues = backendIssues ?? validation.issues;
  const invalidNodeIds = useMemo(
    () => new Set(showValidation ? validationIssues.flatMap((issue) => issue.nodeId ? [issue.nodeId] : []) : []),
    [showValidation, validationIssues],
  );
  const displayedNodes = useMemo(
    () => nodes.map((node) => ({ ...node, data: { ...node.data, validationError: invalidNodeIds.has(node.id) } })),
    [nodes, invalidNodeIds],
  );
  const invalidEdgeIds = useMemo(
    () => new Set(showValidation ? validationIssues.flatMap((issue) => issue.edgeId ? [issue.edgeId] : []) : []),
    [showValidation, validationIssues],
  );
  const displayedEdges = useMemo(
    () => edges.map((edge) => ({ ...edge, className: invalidEdgeIds.has(edge.id) ? 'validation-error-edge' : '' })),
    [edges, invalidEdgeIds],
  );

  const currentDocument = (): CanvasDocument => toCanvasDocument(workflow, module, getViewport(), nodes, edges);

  const persistCurrent = (): CanvasWorkspace => {
    const document = currentDocument();
    const next = {
      activeWorkflowId: document.workflow.id,
      drafts: { ...workspaceRef.current.drafts, [document.workflow.id]: document },
    };
    saveWorkspace(next);
    workspaceRef.current = next;
    setDrafts(next.drafts);
    return next;
  };

  useEffect(() => {
    if (!canPersist) return;
    const timer = window.setTimeout(() => {
      try {
        const document = toCanvasDocument(workflow, module, viewport, nodes, edges);
        const next = {
          activeWorkflowId: document.workflow.id,
          drafts: { ...workspaceRef.current.drafts, [document.workflow.id]: document },
        };
        saveWorkspace(next);
        workspaceRef.current = next;
        setDrafts(next.drafts);
      } catch (error) {
        setNotice(`自动保存失败：${error instanceof Error ? error.message : String(error)}`);
      }
    }, 350);
    return () => window.clearTimeout(timer);
  }, [workflow, module, nodes, edges, viewport, canPersist]);

  useEffect(() => {
    setBackendIssues(null);
  }, [workflow, module, nodes, edges]);

  const restoreDocument = (document: CanvasDocument) => {
    const flow = fromCanvasDocument(document);
    setWorkflow(document.workflow);
    setModule(document.module);
    setNodes(flow.nodes);
    setEdges(flow.edges);
    setSavedViewport(flow.viewport);
    setSelectedNodeId(null);
    setSelectedEdgeId(null);
    setShowValidation(false);
    setBackendIssues(null);
    window.requestAnimationFrame(() => setViewport(flow.viewport));
  };

  const changeWorkflow = (id: string) => {
    if (id === workflow.id) return;
    try {
      const saved = persistCurrent();
      const target = saved.drafts[id];
      if (!target) throw new Error('找不到所选工作流。');
      const next = { ...saved, activeWorkflowId: id };
      saveWorkspace(next);
      workspaceRef.current = next;
      restoreDocument(target);
      setNotice(`已打开工作流：${target.workflow.name}`);
    } catch (error) {
      setNotice(`切换失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const newWorkflow = () => {
    if (!canPersist && !window.confirm('本地草稿无法读取。新建并保存会覆盖损坏的本地数据，确定继续吗？')) return;
    try {
      const saved = canPersist ? persistCurrent() : workspaceRef.current;
      const blank = createBlankDocument();
      const next = { activeWorkflowId: blank.workflow.id, drafts: { ...saved.drafts, [blank.workflow.id]: blank } };
      saveWorkspace(next);
      workspaceRef.current = next;
      setDrafts(next.drafts);
      setCanPersist(true);
      restoreDocument(blank);
      setNotice('已新建工作流。');
    } catch (error) {
      setNotice(`新建失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const deleteWorkflow = () => {
    if (!window.confirm(`确定删除本地草稿“${workflow.name}”吗？此操作无法撤销。`)) return;
    try {
      const nextDrafts = { ...workspaceRef.current.drafts };
      delete nextDrafts[workflow.id];
      const fallback = Object.values(nextDrafts)[0] || createBlankDocument();
      nextDrafts[fallback.workflow.id] = fallback;
      const next = { activeWorkflowId: fallback.workflow.id, drafts: nextDrafts };
      saveWorkspace(next);
      workspaceRef.current = next;
      setDrafts(nextDrafts);
      restoreDocument(fallback);
      setNotice('已删除本地工作流草稿。');
    } catch (error) {
      setNotice(`删除失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const importFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    try {
      const document = await readCanvasFile(file);
      if (workspaceRef.current.drafts[document.workflow.id] &&
          !window.confirm(`工作流 ID ${document.workflow.id} 已存在。确定用导入的文件覆盖这份本地草稿吗？`)) return;
      const saved = canPersist ? persistCurrent() : workspaceRef.current;
      const next = {
        activeWorkflowId: document.workflow.id,
        drafts: { ...saved.drafts, [document.workflow.id]: document },
      };
      saveWorkspace(next);
      workspaceRef.current = next;
      setDrafts(next.drafts);
      setCanPersist(true);
      restoreDocument(document);
      setNotice(`已导入工作流：${document.workflow.name}`);
    } catch (error) {
      setNotice(`导入失败，当前画布未替换：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const exportJson = () => {
    try {
      downloadCanvasFile(parseCanvasDocument(currentDocument()));
      setNotice('已导出画布 JSON 草稿。配置检查不影响草稿导出。');
    } catch (error) {
      setNotice(`导出失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const checkConfiguration = () => {
    setBackendIssues(null);
    setShowValidation(true);
    setNotice(validation.issues.length === 0
      ? `顺序工作流校验通过，执行顺序共 ${validation.orderedNodeIds?.length || 0} 个 Agent。`
      : `发现 ${validation.issues.length} 项工作流问题。点击问题可定位节点或连线。`);
  };

  const publishWorkflow = async () => {
    const document = currentDocument();
    const localValidation = validateSequentialWorkflow(document);
    setShowValidation(true);
    setBackendIssues(null);
    if (localValidation.issues.length > 0) {
      setNotice(`本地校验发现 ${localValidation.issues.length} 项问题，修改后再发布。`);
      return;
    }

    setPublishing(true);
    setNotice('正在发布工作流…');
    try {
      const result = await requestWorkflowPublish(document);
      setBackendIssues(result.issues);
      if (!result.published) {
        setNotice(`后端校验发现 ${result.issues.length} 项问题。点击问题可定位节点或连线。`);
        return;
      }
      setNotice(`发布成功：${result.workflowId}。现在可以在对话页选择这个工作流。`);
    } catch (error) {
      setNotice(`发布失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setPublishing(false);
    }
  };

  const locateIssue = (nodeId?: string, edgeId?: string) => {
    if (nodeId) {
      setSelectedNodeId(nodeId);
      setSelectedEdgeId(null);
      void fitView({ nodes: [{ id: nodeId }], padding: 0.5, duration: 250 });
    } else if (edgeId) {
      const edge = edges.find((item) => item.id === edgeId);
      setSelectedNodeId(null);
      setSelectedEdgeId(edgeId);
      if (edge) void fitView({ nodes: [{ id: edge.source }, { id: edge.target }], padding: 0.5, duration: 250 });
    }
  };

  const createNode = useCallback((position: { x: number; y: number }) => {
    const id = `n_${crypto.randomUUID()}`;
    const node: AgentFlowNode = {
      id,
      type: 'agent',
      position,
      data: { config: { name: '', description: '', instruction: '' } },
    };
    setNodes((current) => [...current, node]);
    setSelectedNodeId(id);
    setSelectedEdgeId(null);
    setNotice('已添加 Agent。请在右侧填写名称、描述和提示词。');
    requestAnimationFrame(() => fitView({ padding: 0.2, duration: 250 }));
  }, [fitView, setNodes]);

  const addFromButton = () => {
    createNode({ x: 80 + (nodes.length % 3) * 280, y: 120 + Math.floor(nodes.length / 3) * 190 });
  };

  const onConnect = useCallback((connection: Connection) => {
    if (connection.source === connection.target) {
      setNotice('不能把节点连接到自身。');
      return;
    }
    if (edges.some((edge) => edge.source === connection.source && edge.target === connection.target)) {
      setNotice('这两个节点之间已有同方向连线。');
      return;
    }
    setEdges((current) => addEdge({
      ...connection,
      id: `e_${crypto.randomUUID()}`,
      markerEnd: { type: MarkerType.ArrowClosed },
    }, current));
    setNotice('已连接节点。点击“校验顺序工作流”检查连线。');
  }, [edges, setEdges]);

  const updateConfig = (field: keyof AgentConfig, value: string) => {
    if (!selectedNodeId) return;
    setNodes((current) => current.map((node) => {
      if (node.id !== selectedNodeId) return node;
      const config = { ...node.data.config };
      if (field === 'outputKey' && !value) delete config.outputKey;
      else config[field] = value;
      return { ...node, data: { ...node.data, config } };
    }));
  };

  const deleteSelected = () => {
    if (selectedNodeId) {
      setNodes((current) => current.filter((node) => node.id !== selectedNodeId));
      setEdges((current) => current.filter((edge) => edge.source !== selectedNodeId && edge.target !== selectedNodeId));
      setSelectedNodeId(null);
      setNotice('已删除节点及关联连线。');
    } else if (selectedEdgeId) {
      setEdges((current) => current.filter((edge) => edge.id !== selectedEdgeId));
      setSelectedEdgeId(null);
      setNotice('已删除连线。');
    }
  };

  const onDrop = (event: React.DragEvent) => {
    event.preventDefault();
    if (event.dataTransfer.getData('application/agent-canvas-node') !== 'agent') return;
    createNode(screenToFlowPosition({ x: event.clientX, y: event.clientY }));
  };

  const overview = useMemo(() => `${nodes.length} 个 Agent · ${edges.length} 条连线`, [nodes.length, edges.length]);

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand"><span className="brand-mark">✦</span><div><strong>群智工坊</strong><small>Agent Canvas / 顺序工作流</small></div></div>
        <div className="top-actions"><span className="model-pill">模型 · {module.chatModel.model}</span><a className="back-link" href="../index.html">返回对话</a></div>
      </header>
      <div className="workspace">
        <aside className="sidebar left-panel">
          <div className="section-label">工作流</div>
          <label>打开本地草稿
            <select className="draft-select" value={workflow.id} onChange={(event) => changeWorkflow(event.target.value)}>
              {Object.values(drafts).map((draft) => <option key={draft.workflow.id} value={draft.workflow.id}>{draft.workflow.name || '未命名工作流'}</option>)}
            </select>
          </label>
          <div className="draft-actions">
            <button onClick={newWorkflow}>＋ 新建</button>
            <button onClick={deleteWorkflow}>删除草稿</button>
          </div>
          <label>工作流名称<input value={workflow.name} onChange={(event) => setWorkflow({ ...workflow, name: event.target.value })} /></label>
          <label>描述<textarea rows={3} value={workflow.description} onChange={(event) => setWorkflow({ ...workflow, description: event.target.value })} placeholder="这条工作流完成什么任务" /></label>
          <div className="draft-actions">
            <button onClick={exportJson}>导出 JSON</button>
            <button onClick={() => fileInputRef.current?.click()}>导入 JSON</button>
          </div>
          <input ref={fileInputRef} type="file" accept=".json,application/json" className="file-input" onChange={importFile} aria-label="选择画布 JSON 文件" />
          <p className="sidebar-tip">编辑后自动保存在当前浏览器。JSON 文件可用于备份和迁移。</p>
          <button className="validate-button" onClick={checkConfiguration}>校验顺序工作流</button>
          <button className="publish-button" onClick={publishWorkflow} disabled={publishing}>
            {publishing ? '正在发布…' : '发布工作流'}
          </button>
          {showValidation && <div className="validation-panel" aria-live="polite">
            <strong>{validationIssues.length === 0 ? '顺序工作流校验通过' : `${validationIssues.length} 项工作流问题`}</strong>
            <p>{validationIssues.length === 0 && validation.orderedNodeIds
              ? `执行顺序：${validation.orderedNodeIds.map((id) => nodes.find((node) => node.id === id)?.data.config.name || id).join(' → ')}`
              : '检查 Agent 配置、输出引用和顺序连线。点击问题可定位。'}</p>
            {validationIssues.length > 0 && <ul>
              {validationIssues.map((issue, index) => <li key={`${issue.code}-${issue.nodeId || issue.edgeId || 'canvas'}-${index}`}>
                <button onClick={() => locateIssue(issue.nodeId, issue.edgeId)} disabled={!issue.nodeId && !issue.edgeId}>{issue.message}</button>
              </li>)}
            </ul>}
          </div>}
          <div className="divider" />
          <div className="section-label">节点面板</div>
          <div className="palette-item" draggable onDragStart={(event) => event.dataTransfer.setData('application/agent-canvas-node', 'agent')}>
            <span className="palette-icon">✧</span><div><strong>Agent 节点</strong><small>拖到画布中</small></div><span className="grab">⠿</span>
          </div>
          <button className="add-button" onClick={addFromButton}>＋ 添加 Agent</button>
          <p className="sidebar-tip">从节点右侧圆点拖向另一节点左侧圆点，即可建立执行连线。</p>
        </aside>
        <main className="canvas-area" aria-label="Agent 工作流画布">
          <div className="canvas-heading"><span>工作流画布</span><span>{overview}</span></div>
          <div className="flow-wrap" onDrop={onDrop} onDragOver={(event) => { event.preventDefault(); event.dataTransfer.dropEffect = 'move'; }}>
            <ReactFlow
              nodes={displayedNodes}
              edges={displayedEdges}
              nodeTypes={nodeTypes}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={(_, node) => { setSelectedNodeId(node.id); setSelectedEdgeId(null); }}
              onEdgeClick={(_, edge) => { setSelectedEdgeId(edge.id); setSelectedNodeId(null); }}
              onPaneClick={() => { setSelectedNodeId(null); setSelectedEdgeId(null); }}
              onNodesDelete={(deleted) => { const ids = new Set(deleted.map((node) => node.id)); setEdges((current) => current.filter((edge) => !ids.has(edge.source) && !ids.has(edge.target))); setSelectedNodeId(null); }}
              onEdgesDelete={() => setSelectedEdgeId(null)}
              defaultEdgeOptions={{ markerEnd: { type: MarkerType.ArrowClosed } }}
              defaultViewport={restored.viewport}
              onMoveEnd={(_, nextViewport) => setSavedViewport(nextViewport)}
              minZoom={0.2}
              maxZoom={2}
            >
              <Background color="#d9e1e8" gap={20} />
              <Controls showInteractive={false} />
            </ReactFlow>
            {nodes.length === 0 && <div className="empty-state"><span>✦</span><strong>从一个 Agent 开始</strong><p>拖入节点，配置任务，再连接执行顺序</p></div>}
          </div>
          <div className="status-bar">{notice}</div>
        </main>
        <aside className="sidebar properties-panel">
          <div className="section-label">属性面板</div>
          {selectedNode ? <>
            <div className="selection-title"><span className="selection-icon">✧</span><div><strong>Agent 配置</strong><small>节点 ID · {selectedNode.id.slice(0, 12)}…</small></div></div>
            <label>内部名称<input value={selectedNode.data.config.name} onChange={(event) => updateConfig('name', event.target.value)} placeholder="例如 requirement_analyzer" /></label>
            <label>描述<input value={selectedNode.data.config.description} onChange={(event) => updateConfig('description', event.target.value)} placeholder="这个 Agent 的职责" /></label>
            <label>提示词<textarea rows={9} value={selectedNode.data.config.instruction} onChange={(event) => updateConfig('instruction', event.target.value)} placeholder="告诉 Agent 要完成什么任务" /></label>
            <label>输出变量（可选）<input value={selectedNode.data.config.outputKey || ''} onChange={(event) => updateConfig('outputKey', event.target.value)} placeholder="例如 requirement_result" /></label>
            <p className="field-hint">下游提示词可通过 {'{输出变量名}'} 使用上游结果。</p>
            <button className="delete-button" onClick={deleteSelected}>删除此 Agent</button>
          </> : selectedEdge ? <>
            <div className="selection-title"><span className="selection-icon">→</span><div><strong>执行连线</strong><small>{selectedEdge.source.slice(0, 12)}… → {selectedEdge.target.slice(0, 12)}…</small></div></div>
            <button className="delete-button" onClick={deleteSelected}>删除此连线</button>
          </> : <div className="no-selection">点击画布中的节点编辑属性，或点击连线进行删除。</div>}
        </aside>
      </div>
    </div>
  );
}

export default function App() {
  return <ReactFlowProvider><CanvasEditor /></ReactFlowProvider>;
}
