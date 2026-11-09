declare global {
  interface Window {
    APP_CONFIG?: { API_BASE?: string };
  }
}

export type RunStatus = 'PLANNING' | 'VALIDATING' | 'CREATING' | 'EXECUTING' | 'SUMMARIZING' | 'COMPLETED' | 'FAILED' | 'TIMEOUT' | 'CANCELLED';
export type TaskStatus = 'PENDING' | 'READY' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'BLOCKED' | 'CANCELLED' | 'TIMED_OUT';

export interface WorkerPlan { workerId: string; roleName: string; responsibility: string; instruction: string }
export interface TaskPlan { taskId: string; workerId: string; title: string; description: string; expectedOutput: string; acceptanceCriteria: string[]; dependsOn: string[] }
export interface TeamPlan { planVersion: number; summary: string; workers: WorkerPlan[]; tasks: TaskPlan[] }
export interface TaskResult { taskId: string; workerId: string; status: TaskStatus; output?: string; failureMessage?: string }
export interface RunSnapshot {
  runId: string;
  userGoal: string;
  acceptanceRequirements: string[];
  status: RunStatus;
  terminationReason?: string;
  currentPlan?: TeamPlan;
  currentTaskResults: TaskResult[];
  usedModelCalls: number;
  remainingModelCalls: number;
  startedAt: string;
  resourcesReleased: boolean;
  lastEventSequence: number;
  finalAnswer?: string;
}
export interface RunCapabilities { enabled: boolean; maxWorkers: number; maxTasks: number; maxConcurrentWorkers: number; stepTimeoutSeconds: number; runTimeoutSeconds: number; maxModelCalls: number; maxUserInputCharacters: number }
export interface RunEvent { sequence: number; eventType: string; runStatus: RunStatus; workerId?: string; taskId?: string; reasonCode?: string; message?: string; occurredAt: string }

interface ApiResponse<T> { code: string; info: string; data?: T }

const apiBase = () => window.APP_CONFIG?.API_BASE || 'http://127.0.0.1:8091';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase()}${path}`, init);
  const envelope = await response.json() as ApiResponse<T>;
  if (!response.ok || envelope.code !== '0000' || envelope.data === undefined) {
    throw new Error(envelope.info || `请求失败（HTTP ${response.status}）`);
  }
  return envelope.data;
}

export const getCapabilities = () => request<RunCapabilities>('/api/v1/dynamic-team/capabilities');
export const getRun = (runId: string) => request<RunSnapshot>(`/api/v1/dynamic-team/runs/${encodeURIComponent(runId)}`);
export const cancelRun = (runId: string) => request<RunSnapshot>(`/api/v1/dynamic-team/runs/${encodeURIComponent(runId)}/cancel`, { method: 'POST' });
export const startRun = (userGoal: string, acceptanceRequirements: string[]) => request<{ runId: string; status: RunStatus }>('/api/v1/dynamic-team/runs', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ userGoal, acceptanceRequirements }),
});
export const streamUrl = (runId: string, afterSequence: number) => `${apiBase()}/api/v1/dynamic-team/runs/${encodeURIComponent(runId)}/events/stream?afterSequence=${afterSequence}`;
export const isTerminal = (status?: RunStatus) => status === 'COMPLETED' || status === 'FAILED' || status === 'TIMEOUT' || status === 'CANCELLED';
