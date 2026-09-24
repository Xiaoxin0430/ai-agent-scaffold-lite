package cn.bugstack.ai.domain.agent.model.valobj;

import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;

/** 本轮流式事件和对应的检索来源。 */
public record ChatStreamResultVO(Flowable<Event> events, List<SimilarDocumentChunkVO> sources) {
}
