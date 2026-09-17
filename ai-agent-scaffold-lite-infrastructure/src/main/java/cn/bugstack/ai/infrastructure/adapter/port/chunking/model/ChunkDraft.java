package cn.bugstack.ai.infrastructure.adapter.port.chunking.model;

/**
 * 已加入相邻上下文、尚未转换为领域实体的文本块草稿。
 *
 * @param coreSpan          当前块独立负责的原文范围
 * @param contextStartOffset 包含 overlap 后的起点
 * @param content           提供给 Embedding 的内容
 * @param tokenCount        内容 token 数
 * @author xiaoxin
 */
public record ChunkDraft(TextSpan coreSpan,
                         int contextStartOffset,
                         String content,
                         int tokenCount) {
}
