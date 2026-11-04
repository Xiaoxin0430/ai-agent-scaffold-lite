import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import ts from 'typescript';

const source = readFileSync(new URL('../src/validation.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
});
const exports = {};
new Function('exports', compiled.outputText)(exports);
const { validateSequentialWorkflow } = exports;

function document(names, links = []) {
  return {
    schemaVersion: '1.0',
    workflow: { id: 'wf_test', name: 'TestWorkflow', description: '' },
    model: 'deepseek-flash',
    editor: { viewport: { x: 0, y: 0, zoom: 1 } },
    nodes: names.map((name, index) => ({
      id: `n${index}`,
      type: 'agent',
      position: { x: index * 100, y: 0 },
      config: { name, description: '', instruction: '完成任务。' },
    })),
    edges: links.map(([source, target], index) => ({ id: `e${index}`, source: `n${source}`, target: `n${target}` })),
  };
}

describe('顺序工作流校验', () => {
  it('接受单个 Agent，并给出执行顺序', () => {
    const result = validateSequentialWorkflow(document(['agent_a']));
    assert.deepEqual(result.issues, []);
    assert.deepEqual(result.orderedNodeIds, ['n0']);
  });

  it('按连线而非节点数组顺序得到执行顺序', () => {
    const result = validateSequentialWorkflow(document(['agent_a', 'agent_b', 'agent_c'], [[2, 0], [0, 1]]));
    assert.deepEqual(result.issues, []);
    assert.deepEqual(result.orderedNodeIds, ['n2', 'n0', 'n1']);
  });

  it('定位分叉和孤立节点', () => {
    const result = validateSequentialWorkflow(document(['agent_a', 'agent_b', 'agent_c', 'agent_d'], [[0, 1], [0, 2]]));
    assert.ok(result.issues.some((issue) => issue.code === 'MULTIPLE_SUCCESSORS'));
    assert.ok(result.issues.some((issue) => issue.code === 'DISCONNECTED_NODE'));
    assert.equal(result.orderedNodeIds, null);
  });

  it('定位环形连线', () => {
    const result = validateSequentialWorkflow(document(['agent_a', 'agent_b'], [[0, 1], [1, 0]]));
    assert.ok(result.issues.some((issue) => issue.code === 'CYCLE' && issue.edgeId === 'e1'));
    assert.equal(result.orderedNodeIds, null);
  });

  it('检查重名、输出冲突和不存在的引用', () => {
    const draft = document(['same', 'same'], [[0, 1]]);
    draft.nodes[0].config.outputKey = 'result';
    draft.nodes[1].config.outputKey = 'result';
    draft.nodes[1].config.instruction = '使用 {missing}。';
    const codes = validateSequentialWorkflow(draft).issues.map((issue) => issue.code);
    for (const code of ['AGENT_NAME_DUPLICATE', 'OUTPUT_KEY_DUPLICATE', 'OUTPUT_REFERENCE_MISSING']) {
      assert.ok(codes.includes(code));
    }
  });

  it('引用只能读取顺序链上更早 Agent 的输出', () => {
    const draft = document(['first', 'second'], [[0, 1]]);
    draft.nodes[1].config.outputKey = 'result';
    draft.nodes[0].config.instruction = '提前读取 {result}。';
    assert.ok(validateSequentialWorkflow(draft).issues.some((issue) => issue.code === 'OUTPUT_REFERENCE_NOT_UPSTREAM'));
  });
});
