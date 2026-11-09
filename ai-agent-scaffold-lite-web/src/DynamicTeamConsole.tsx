import { useCallback, useEffect, useRef, useState, type CSSProperties, type PointerEvent, type ReactNode } from 'react';
import { ChartBar, CheckCircle, ClipboardText, Flag, FlowArrow, GearSix, Hourglass, MagnifyingGlass, Play, Robot, Stop, UsersThree, WarningCircle } from '@phosphor-icons/react';
import { cancelRun, getCapabilities, getRun, isTerminal, startRun, streamUrl, type RunCapabilities, type RunEvent, type RunSnapshot, type TaskPlan, type TaskResult, type WorkerPlan } from './dynamicTeamApi';
import plannerImage from './assets/dynamic-team/planner.png';
import summarizerImage from './assets/dynamic-team/summarizer.png';
import tableImage from './assets/dynamic-team/table.png';
import workerImage1 from './assets/dynamic-team/worker-1.png';
import workerImage2 from './assets/dynamic-team/worker-2.png';
import workerImage3 from './assets/dynamic-team/worker-3.png';
import './dynamic-team.css';

type AgentKind = 'planner' | 'worker' | 'summarizer';
interface AgentView {
  id: string;
  kind: AgentKind;
  title: string;
  subtitle: string;
  status: string;
  tasks: Array<{ title: string; status: string }>;
}

const statusText: Record<string, string> = {
  PLANNING: '规划中', VALIDATING: '校验中', CREATING: '创建中', EXECUTING: '执行中', SUMMARIZING: '汇总中',
  COMPLETED: '已完成', FAILED: '失败', TIMEOUT: '超时', CANCELLED: '已取消', PENDING: '待执行', READY: '就绪', RUNNING: '运行中',
  SUCCEEDED: '已完成', BLOCKED: '已阻断', TIMED_OUT: '超时',
};

function taskStatus(task: TaskPlan, results: TaskResult[]): string {
  const result = results.find((item) => item.taskId === task.taskId);
  return result?.status || 'PENDING';
}

function workerStatus(worker: WorkerPlan, snapshot: RunSnapshot): string {
  const tasks = snapshot.currentPlan?.tasks.filter((task) => task.workerId === worker.workerId) || [];
  const statuses = tasks.map((task) => taskStatus(task, snapshot.currentTaskResults));
  if (statuses.includes('RUNNING')) return 'RUNNING';
  if (statuses.some((status) => ['FAILED', 'BLOCKED', 'TIMED_OUT', 'CANCELLED'].includes(status))) return statuses.find((status) => ['FAILED', 'BLOCKED', 'TIMED_OUT', 'CANCELLED'].includes(status)) || 'FAILED';
  if (statuses.length > 0 && statuses.every((status) => status === 'SUCCEEDED')) return 'SUCCEEDED';
  return snapshot.status === 'EXECUTING' ? 'READY' : 'PENDING';
}

function makeAgents(snapshot?: RunSnapshot): AgentView[] {
  const plan = snapshot?.currentPlan;
  const workers = plan?.workers || [];
  const plannerStatus = snapshot && ['PLANNING', 'VALIDATING', 'CREATING'].includes(snapshot.status) ? 'RUNNING' : plan ? 'SUCCEEDED' : 'PENDING';
  const agents: AgentView[] = [{ id: 'planner', kind: 'planner', title: '规划智能体', subtitle: '分解目标 · 分配任务', status: plannerStatus, tasks: [] }];

  for (const worker of workers) {
    const tasks = plan?.tasks.filter((task) => task.workerId === worker.workerId) || [];
    agents.push({
      id: worker.workerId,
      kind: 'worker',
      title: worker.roleName,
      subtitle: worker.responsibility,
      status: snapshot ? workerStatus(worker, snapshot) : 'PENDING',
      tasks: tasks.map((task) => ({ title: task.title, status: snapshot ? taskStatus(task, snapshot.currentTaskResults) : 'PENDING' })),
    });
  }

  agents.push({
    id: 'summarizer', kind: 'summarizer', title: '汇总智能体', subtitle: '整合结果 · 形成交付',
    status: snapshot?.status === 'SUMMARIZING' ? 'RUNNING' : snapshot?.status === 'COMPLETED' ? 'SUCCEEDED' : snapshot && ['FAILED', 'TIMEOUT', 'CANCELLED'].includes(snapshot.status) ? snapshot.status : 'PENDING',
    tasks: [],
  });
  return agents;
}

function InlineMarkdown({ text }: { text: string }) {
  const parts: ReactNode[] = [];
  const tokenPattern = /(\*\*([^*]+)\*\*|__([^_]+)__|`([^`]+)`|\*([^*]+)\*)/g;
  let cursor = 0;
  for (const match of text.matchAll(tokenPattern)) {
    const index = match.index ?? 0;
    if (index > cursor) parts.push(text.slice(cursor, index));
    if (match[2] || match[3]) parts.push(<strong key={index}>{match[2] || match[3]}</strong>);
    else if (match[4]) parts.push(<code key={index}>{match[4]}</code>);
    else parts.push(<em key={index}>{match[5]}</em>);
    cursor = index + match[0].length;
  }
  if (cursor < text.length) parts.push(text.slice(cursor));
  return <>{parts}</>;
}

function cleanTableRow(line: string): string[] {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim());
}

function MarkdownContent({ text }: { text: string }) {
  const lines = text.replace(/\r\n?/g, '\n').split('\n');
  const blocks: ReactNode[] = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index].trim();
    if (!line || /^[-*_]{3,}$/.test(line)) { index += 1; continue; }

    if (/^\|.*\|$/.test(line)) {
      const rows: string[][] = [];
      while (index < lines.length && /^\s*\|.*\|\s*$/.test(lines[index])) {
        const cells = cleanTableRow(lines[index]);
        const divider = cells.every((cell) => /^:?-{2,}:?$/.test(cell));
        if (!divider) rows.push(cells);
        index += 1;
      }
      if (rows.length) {
        const [header, ...body] = rows;
        blocks.push(<div className="answer-table-wrap" key={`table-${index}`}><table><thead><tr>{header.map((cell, cellIndex) => <th key={cellIndex}><InlineMarkdown text={cell} /></th>)}</tr></thead><tbody>{body.map((row, rowIndex) => <tr key={rowIndex}>{header.map((_, cellIndex) => <td key={cellIndex}><InlineMarkdown text={row[cellIndex] || ''} /></td>)}</tr>)}</tbody></table></div>);
      }
      continue;
    }

    const heading = line.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      const level = heading[1].length;
      const content = <InlineMarkdown text={heading[2].replace(/\s+#+$/, '')} />;
      if (level === 1) blocks.push(<h2 key={index}>{content}</h2>);
      else if (level === 2) blocks.push(<h3 key={index}>{content}</h3>);
      else blocks.push(<h4 key={index}>{content}</h4>);
      index += 1;
      continue;
    }

    if (/^\s*[-*+]\s+/.test(lines[index])) {
      const items: string[] = [];
      while (index < lines.length && /^\s*[-*+]\s+/.test(lines[index])) items.push(lines[index++].replace(/^\s*[-*+]\s+/, ''));
      blocks.push(<ul key={`list-${index}`}>{items.map((item, itemIndex) => <li key={itemIndex}><InlineMarkdown text={item} /></li>)}</ul>);
      continue;
    }

    if (/^\s*\d+[.)]\s+/.test(lines[index])) {
      const items: string[] = [];
      while (index < lines.length && /^\s*\d+[.)]\s+/.test(lines[index])) items.push(lines[index++].replace(/^\s*\d+[.)]\s+/, ''));
      blocks.push(<ol key={`list-${index}`}>{items.map((item, itemIndex) => <li key={itemIndex}><InlineMarkdown text={item} /></li>)}</ol>);
      continue;
    }

    const paragraph = [line];
    index += 1;
    while (index < lines.length && lines[index].trim() && !/^\s*(#{1,4}\s|[-*+]\s|\d+[.)]\s|\|.*\|)/.test(lines[index])) paragraph.push(lines[index++].trim());
    blocks.push(<p key={`paragraph-${index}`}><InlineMarkdown text={paragraph.join(' ')} /></p>);
  }
  return <div className="answer-markdown">{blocks}</div>;
}

const workerImages = [workerImage1, workerImage2, workerImage3];
const workerSeats: Record<number, string[]> = {
  1: ['left-middle'],
  2: ['left-middle', 'right-middle'],
  3: ['left-top', 'right-top', 'left-bottom'],
  4: ['left-top', 'right-top', 'left-bottom', 'right-bottom'],
  5: ['left-top', 'right-top', 'left-middle', 'right-middle', 'left-bottom'],
};

function SceneSeat({ agent, image, seat, selected, onSelect }: { agent: AgentView; image: string; seat: string; selected: boolean; onSelect: () => void }) {
  return <button type="button" className={`scene-seat ${seat} ${agent.status.toLowerCase()}${selected ? ' selected' : ''}`} onClick={onSelect} aria-label={`${agent.title}，${statusText[agent.status] || agent.status}`}>
    <img src={image} alt="" draggable={false} />
    <span className="seat-caption"><span className="seat-caption-head"><strong>{agent.title}</strong><span className={`seat-status ${agent.status.toLowerCase()}`}><i />{statusText[agent.status] || agent.status}</span></span><small>{agent.kind === 'worker' && agent.tasks.length ? agent.tasks.map((task) => task.title).join('、') : agent.subtitle}</small></span>
  </button>;
}

function Console() {
  const [goal, setGoal] = useState('调研并分析目标主题，形成结构化结论与可执行建议。');
  const [requirements, setRequirements] = useState('结论完整\n说明关键依据');
  const [capabilities, setCapabilities] = useState<RunCapabilities>();
  const [snapshot, setSnapshot] = useState<RunSnapshot>();
  const [events, setEvents] = useState<RunEvent[]>([]);
  const [selectedId, setSelectedId] = useState<string>('planner');
  const [notice, setNotice] = useState('输入任务目标后开始运行。');
  const [starting, setStarting] = useState(false);
  const [rightTab, setRightTab] = useState<'details' | 'delivery'>('details');
  const [bottomTab, setBottomTab] = useState<'events' | 'dependencies'>('events');
  const [leftWidth, setLeftWidth] = useState(270);
  const [rightWidth, setRightWidth] = useState(320);
  const drag = useRef<{ side: 'left' | 'right'; startX: number; width: number }>();
  const cursor = useRef(0);

  useEffect(() => { getCapabilities().then(setCapabilities).catch((error: Error) => setNotice(error.message)); }, []);
  const refresh = useCallback(async (runId: string) => {
    const next = await getRun(runId);
    cursor.current = Math.max(cursor.current, next.lastEventSequence);
    setSnapshot(next);
    return next;
  }, []);

  useEffect(() => {
    if (!snapshot || !isTerminal(snapshot.status) || snapshot.status === 'COMPLETED') return;
    setRightTab('delivery');
    const reason = snapshot.terminationReason === 'PLANNING_FAILED'
      ? '规划模型未生成有效计划，请稍后重试；若反复出现，请检查模型服务。'
      : snapshot.status === 'TIMEOUT' ? '运行超时，请缩小任务范围后重试。'
      : snapshot.status === 'CANCELLED' ? '运行已取消。'
      : `运行失败：${snapshot.terminationReason || '未知原因'}`;
    setNotice(reason);
  }, [snapshot?.status, snapshot?.terminationReason, snapshot?.runId]);

  useEffect(() => {
    if (!snapshot?.runId || isTerminal(snapshot.status)) return;
    const source = new EventSource(streamUrl(snapshot.runId, cursor.current));
    const onEvent = (raw: MessageEvent<string>) => {
      const event = JSON.parse(raw.data) as RunEvent;
      cursor.current = Math.max(cursor.current, event.sequence);
      setEvents((current) => [event, ...current.filter((item) => item.sequence !== event.sequence)].slice(0, 20));
      void refresh(snapshot.runId);
    };
    source.addEventListener('dynamic-team-event', onEvent as EventListener);
    source.onerror = () => setNotice('实时连接暂时中断，正在通过快照恢复。');
    const fallback = window.setInterval(() => void refresh(snapshot.runId), 2500);
    return () => { source.close(); window.clearInterval(fallback); };
  }, [snapshot?.runId, snapshot?.status, refresh]);

  useEffect(() => {
    const onPointerMove = (event: globalThis.PointerEvent) => {
      const current = drag.current;
      if (!current) return;
      const delta = event.clientX - current.startX;
      if (current.side === 'left') setLeftWidth(Math.min(480, Math.max(200, current.width + delta)));
      else setRightWidth(Math.min(560, Math.max(260, current.width - delta)));
    };
    const onPointerUp = () => { drag.current = undefined; document.body.classList.remove('team-resizing'); };
    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
    return () => { window.removeEventListener('pointermove', onPointerMove); window.removeEventListener('pointerup', onPointerUp); document.body.classList.remove('team-resizing'); };
  }, []);

  const agents = makeAgents(snapshot);
  const selectedAgent = agents.find((agent) => agent.id === selectedId) || agents[0];
  const selectedWorker = snapshot?.currentPlan?.workers.find((worker) => worker.workerId === selectedAgent?.id);

  const beginResize = (side: 'left' | 'right') => (event: PointerEvent<HTMLButtonElement>) => {
    event.preventDefault();
    drag.current = { side, startX: event.clientX, width: side === 'left' ? leftWidth : rightWidth };
    document.body.classList.add('team-resizing');
  };
  const begin = async () => {
    if (!goal.trim()) { setNotice('请先输入任务目标。'); return; }
    setStarting(true); setEvents([]); setRightTab('details'); setBottomTab('events'); cursor.current = 0;
    try {
      const accepted = await startRun(goal.trim(), requirements.split('\n').map((item) => item.trim()).filter(Boolean));
      const next = await refresh(accepted.runId);
      setSelectedId('planner');
      setNotice(`运行 ${next.runId.slice(0, 8)} 已受理。`);
    } catch (error) { setNotice(error instanceof Error ? error.message : String(error)); }
    finally { setStarting(false); }
  };

  const stop = async () => {
    if (!snapshot || isTerminal(snapshot.status)) return;
    try { setSnapshot(await cancelRun(snapshot.runId)); setNotice('取消请求已提交。'); }
    catch (error) { setNotice(error instanceof Error ? error.message : String(error)); }
  };

  const running = snapshot && !isTerminal(snapshot.status);
  const phases = ['规划任务', '创建团队', '协作执行', '汇总交付'];
  const phaseIndex = !snapshot ? -1 : ['PLANNING', 'VALIDATING'].includes(snapshot.status) ? 0 : snapshot.status === 'CREATING' ? 1 : snapshot.status === 'EXECUTING' ? 2 : snapshot.status === 'SUMMARIZING' ? 3 : snapshot.status === 'COMPLETED' ? 4 : -1;
  const taskById = new Map(snapshot?.currentPlan?.tasks.map((task) => [task.taskId, task]) || []);
  const dependencies = snapshot?.currentPlan?.tasks.flatMap((task) => task.dependsOn.map((id) => ({ from: taskById.get(id)?.title || id, to: task.title }))) || [];

  return <div className="team-shell">
    <header className="team-topbar">
      <div className="team-brand"><span><FlowArrow size={25} weight="bold" /></span><strong>动态 Agent 团队 <em>·</em> 运行控制台</strong></div>
      <div className="team-top-actions"><span className="top-current"><Play size={16} weight="fill" />任务运行</span><span className="team-capability"><GearSix size={16} />{capabilities === undefined ? '能力读取中' : capabilities.enabled ? `最多 ${capabilities.maxWorkers} 个子智能体 · 并发 ${capabilities.maxConcurrentWorkers}` : '动态团队功能未启用'}</span></div>
    </header>
    <div className="team-layout" style={{ '--left-width': `${leftWidth}px`, '--right-width': `${rightWidth}px` } as CSSProperties}>
      <aside className="team-left">
        <h2><Flag size={20} />任务配置</h2>
        <div className="team-field"><label htmlFor="team-goal">任务目标 <span>*</span></label><textarea id="team-goal" value={goal} onChange={(event) => setGoal(event.target.value)} rows={6} maxLength={capabilities?.maxUserInputCharacters} disabled={!!running} /><small>{goal.length}{capabilities ? ` / ${capabilities.maxUserInputCharacters}` : ''}</small></div>
        <div className="team-field"><label htmlFor="team-requirements">验收要求</label><textarea id="team-requirements" value={requirements} onChange={(event) => setRequirements(event.target.value)} rows={7} disabled={!!running} /><small>每行一项要求</small></div>
        <div className="constraint-card"><h2><Hourglass size={20} />运行限制</h2><div><UsersThree size={18} /><span>最多子智能体数</span><b>{capabilities ? `≤ ${capabilities.maxWorkers}` : '—'}</b></div><div><FlowArrow size={18} /><span>并发执行数</span><b>{capabilities ? `≤ ${capabilities.maxConcurrentWorkers}` : '—'}</b></div><div><Hourglass size={18} /><span>单任务时限</span><b>{capabilities ? `≤ ${capabilities.stepTimeoutSeconds} 秒` : '—'}</b></div><div><Hourglass size={18} /><span>整次运行时限</span><b>{capabilities ? `≤ ${capabilities.runTimeoutSeconds} 秒` : '—'}</b></div><div><ChartBar size={18} /><span>模型调用预算</span><b>{snapshot ? `${snapshot.usedModelCalls} / ${snapshot.usedModelCalls + snapshot.remainingModelCalls}` : capabilities ? `≤ ${capabilities.maxModelCalls}` : '—'}</b></div></div>
        <div className="team-command-actions"><button className="start-run" disabled={starting || !!running || capabilities?.enabled === false} onClick={begin}><Play size={18} weight="fill" />{starting ? '启动中' : '开始运行'}</button><button className="stop-run" disabled={!running} onClick={stop}><Stop size={17} weight="fill" />终止运行</button></div>
      </aside>
      <button className="team-resizer" type="button" aria-label="拖动调整任务配置栏宽度" onPointerDown={beginResize('left')} />
      <main className="team-main">
        <div className="phase-strip">{phases.map((phase, index) => <div key={phase} className={phaseIndex > index ? 'done' : phaseIndex === index ? 'active' : ''}><span className="phase-mark"><CheckCircle size={17} weight={phaseIndex > index ? 'fill' : 'regular'} /></span>{phase}</div>)}</div>
        <div className="team-workspace">
          <div className="team-workspace-heading"><div><span className="eyebrow">TEAM EXECUTION</span><h1>{snapshot?.userGoal || '等待新任务开始'}</h1><p><span className={`run-state ${snapshot?.status?.toLowerCase() || 'pending'}`}>{snapshot ? statusText[snapshot.status] : '待运行'}</span>{snapshot?.currentPlan?.summary || '输入目标后，由规划智能体自动组建团队并分配任务。'}</p></div><span className="worker-count"><UsersThree size={17} />{snapshot?.currentPlan ? `${snapshot.currentPlan.workers.length} 个子智能体` : '团队尚未生成'}</span></div>
          <div className="team-scene" aria-label="动态团队围桌协作场景">
            <SceneSeat agent={agents[0]} image={plannerImage} seat="planner-seat" selected={selectedId === agents[0].id} onSelect={() => { setSelectedId(agents[0].id); setRightTab('details'); }} />
            {agents.slice(1, -1).map((agent, index) => <SceneSeat key={agent.id} agent={agent} image={workerImages[index % workerImages.length]} seat={workerSeats[agents.length - 2]?.[index] || 'left-middle'} selected={selectedId === agent.id} onSelect={() => { setSelectedId(agent.id); setRightTab('details'); }} />)}
            <img className="meeting-table" src={tableImage} alt="" draggable={false} />
            <SceneSeat agent={agents[agents.length - 1]} image={summarizerImage} seat="summarizer-seat" selected={selectedId === agents[agents.length - 1].id} onSelect={() => { setSelectedId(agents[agents.length - 1].id); setRightTab('details'); }} />
            {!snapshot?.currentPlan && <div className="scene-empty"><Robot size={18} />{snapshot && ['PLANNING', 'VALIDATING', 'CREATING'].includes(snapshot.status) ? '规划智能体正在组建团队…' : '开始运行后，规划智能体会安排桌边座位'}</div>}
          </div>
          <div className="team-notice" role="status">{notice}</div>
        </div>
        <section className="activity-panel"><div className="activity-tabs"><button className={bottomTab === 'events' ? 'active' : ''} onClick={() => setBottomTab('events')}><ClipboardText size={17} />执行动态</button><button className={bottomTab === 'dependencies' ? 'active' : ''} onClick={() => setBottomTab('dependencies')}><FlowArrow size={17} />任务依赖</button></div>{bottomTab === 'events' ? <div className="event-list">{events.length ? events.map((event) => <div key={event.sequence}><time>{new Date(event.occurredAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</time><span className="event-dot" /><p><b>{event.message || event.eventType}</b><small>{event.workerId || event.taskId || statusText[event.runStatus]}</small></p></div>) : <p className="empty-events">开始运行后，执行事件会显示在这里。</p>}</div> : <div className="dependency-list">{dependencies.length ? dependencies.map((item, index) => <div key={`${item.from}-${item.to}-${index}`}><span>{item.from}</span><FlowArrow size={16} /><strong>{item.to}</strong></div>) : <p className="empty-events">当前计划没有跨任务依赖。</p>}</div>}</section>
      </main>
      <button className="team-resizer" type="button" aria-label="拖动调整详情栏宽度" onPointerDown={beginResize('right')} />
      <aside className="team-right"><div className="right-tabs"><button className={rightTab === 'details' ? 'active' : ''} onClick={() => setRightTab('details')}><Robot size={17} />智能体详情</button><button className={rightTab === 'delivery' ? 'active' : ''} onClick={() => setRightTab('delivery')}><ClipboardText size={17} />最终交付</button></div>
        {rightTab === 'details' ? <div className="detail-content"><div className="detail-title"><span>{selectedAgent.kind === 'worker' ? <Robot size={27} /> : selectedAgent.kind === 'planner' ? <ClipboardText size={27} /> : <ChartBar size={27} />}</span><div><strong>{selectedAgent.title}</strong><small>{statusText[selectedAgent.status] || selectedAgent.status}</small></div></div><dl><dt><UsersThree size={17} />职责</dt><dd>{selectedWorker?.responsibility || selectedAgent.subtitle}</dd><dt><Flag size={17} />分配任务</dt><dd>{selectedAgent.tasks.length ? selectedAgent.tasks.map((task, index) => <div className="detail-task" key={`${task.title}-${index}`}><span>{task.title}</span><b>{statusText[task.status] || task.status}</b></div>) : selectedAgent.kind === 'planner' ? '理解目标并生成可执行计划' : '等待成功任务结果后统一汇总'}</dd><dt><MagnifyingGlass size={17} />运行标识</dt><dd>{snapshot?.runId || '尚未创建运行'}</dd></dl></div> : <div className="delivery-content"><h2>最终交付</h2>{snapshot?.finalAnswer ? <MarkdownContent text={snapshot.finalAnswer} /> : <div className="delivery-empty"><ClipboardText size={31} /><strong>{snapshot && isTerminal(snapshot.status) ? '本次运行没有生成交付内容' : '交付内容尚未生成'}</strong><span>{snapshot && isTerminal(snapshot.status) ? '请查看下方运行结果。' : '团队完成执行和汇总后，结果会显示在这里。'}</span></div>}{snapshot && ['FAILED', 'TIMEOUT', 'CANCELLED'].includes(snapshot.status) && <div className="failure-box"><WarningCircle size={20} /><div><strong>{statusText[snapshot.status]}</strong><p>{snapshot.terminationReason === 'PLANNING_FAILED' ? '规划模型未生成有效计划，请稍后重试；若反复出现，请检查模型服务。' : snapshot.terminationReason}</p></div></div>}</div>}
      </aside>
    </div>
  </div>;
}

export default function DynamicTeamConsole() { return <Console />; }
