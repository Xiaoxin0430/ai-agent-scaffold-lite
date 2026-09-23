package cn.bugstack.ai.test.trigger;

import cn.bugstack.ai.api.dto.RagAnswerRequestDTO;
import cn.bugstack.ai.api.dto.RagAnswerResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.valobj.RagAnswerVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.domain.rag.service.IDocumentRagService;
import cn.bugstack.ai.trigger.http.RagServiceController;
import cn.bugstack.ai.types.enums.ResponseCode;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

/**
 * RAG HTTP 入站适配器测试。
 *
 * @author xiaoxin
 */
public class RagServiceControllerTest {

    @Test
    public void test_answer_maps_domain_result_to_api_response() {
        IDocumentRagService documentRagService = Mockito.mock(IDocumentRagService.class);
        Mockito.when(documentRagService.answerQuestion(
                        "报销上限是多少？", 3, 0.2d, List.of("doc-1")))
                .thenReturn(RagAnswerVO.builder()
                        .query("报销上限是多少？")
                        .answer("上限为 500 元。[S1]")
                        .sources(List.of(SimilarDocumentChunkVO.builder()
                                .chunk(DocumentChunkEntity.builder()
                                        .chunkId("chunk-1")
                                        .documentId("doc-1")
                                        .chunkIndex(0)
                                        .content("报销上限为 500 元。")
                                        .build())
                                .fileName("policy.txt")
                                .similarity(0.91d)
                                .build()))
                        .build());
        RagServiceController controller = new RagServiceController();
        ReflectionTestUtils.setField(controller, "documentRagService", documentRagService);
        RagAnswerRequestDTO requestDTO = new RagAnswerRequestDTO();
        requestDTO.setQuery("报销上限是多少？");
        requestDTO.setTopK(3);
        requestDTO.setMinSimilarity(0.2d);
        requestDTO.setDocumentIds(List.of("doc-1"));

        Response<RagAnswerResponseDTO> response = controller.answer(requestDTO);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals("上限为 500 元。[S1]", response.getData().getAnswer());
        Assert.assertEquals(1, response.getData().getSources().size());
        Assert.assertEquals("chunk-1",
                response.getData().getSources().get(0).getChunkId());
    }

}
