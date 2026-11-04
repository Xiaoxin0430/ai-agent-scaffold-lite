import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import ts from 'typescript';

const source = readFileSync(new URL('../src/publish.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
});
const exports = {};
new Function('exports', compiled.outputText)(exports);
const { requestWorkflowPublish } = exports;

const document = { workflow: { id: 'wf_test' }, nodes: [], edges: [] };

describe('工作流发布接口', () => {
  it('发送画布并解包发布结果', async () => {
    global.window = { APP_CONFIG: { API_BASE: 'http://backend' } };
    let requestedUrl;
    let requestedBody;
    global.fetch = async (url, init) => {
      requestedUrl = url;
      requestedBody = JSON.parse(init.body);
      return { ok: true, status: 200, json: async () => ({
        code: '0000', info: '发布成功', data: { published: true, workflowId: 'wf_test', issues: [] },
      }) };
    };

    const result = await requestWorkflowPublish(document);

    assert.equal(requestedUrl, 'http://backend/api/v1/canvas/publish');
    assert.deepEqual(requestedBody, document);
    assert.equal(result.workflowId, 'wf_test');
    assert.equal(result.published, true);
  });

  it('保留业务校验问题供页面定位', async () => {
    global.window = { APP_CONFIG: { API_BASE: 'http://backend' } };
    global.fetch = async () => ({ ok: true, status: 200, json: async () => ({
      code: '0002', info: '画布校验未通过',
      data: { published: false, workflowId: null, issues: [{ code: 'WORKFLOW_INVALID', message: '名称无效' }] },
    }) });

    const result = await requestWorkflowPublish(document);

    assert.equal(result.published, false);
    assert.equal(result.issues[0].code, 'WORKFLOW_INVALID');
  });

  it('重复 ID 显示冲突原因', async () => {
    global.window = { APP_CONFIG: { API_BASE: 'http://backend' } };
    global.fetch = async () => ({ ok: true, status: 200, json: async () => ({
      code: '0004', info: '工作流 ID 已发布，请使用新的 ID。', data: null,
    }) });

    await assert.rejects(requestWorkflowPublish(document), /工作流 ID 已发布/);
  });
});
