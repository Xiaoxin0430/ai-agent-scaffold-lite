import { parseCanvasDocument, type CanvasDocument } from './protocol';

export async function readCanvasFile(file: File): Promise<CanvasDocument> {
  let value: unknown;
  try {
    value = JSON.parse(await file.text());
  } catch {
    throw new Error('文件不是有效的 JSON。');
  }
  return parseCanvasDocument(value);
}

export function downloadCanvasFile(document: CanvasDocument): void {
  const blob = new Blob([JSON.stringify(document, null, 2)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = window.document.createElement('a');
  link.href = url;
  link.download = `${document.workflow.name || document.workflow.id}.canvas.json`;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
