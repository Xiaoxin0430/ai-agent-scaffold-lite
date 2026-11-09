package cn.bugstack.ai.test.trigger;

import cn.bugstack.ai.api.dto.IndexDocumentResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.rag.model.valobj.DocumentIndexResultVO;
import cn.bugstack.ai.domain.rag.service.IDocumentRagService;
import cn.bugstack.ai.trigger.http.RagServiceController;
import cn.bugstack.ai.types.enums.ResponseCode;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;


/**
 * RAG HTTP 入站适配器测试。
 *
 * @author xiaoxin
 */
public class RagServiceControllerTest {

    @Test
    public void test_upload_passes_user_to_document_index() {
        IDocumentRagService service = Mockito.mock(IDocumentRagService.class);
        Mockito.when(service.indexDocument(Mockito.eq("doc-1"), Mockito.eq("policy.txt"),
                        Mockito.any(java.io.InputStream.class), Mockito.eq("admin")))
                .thenReturn(DocumentIndexResultVO.builder()
                        .documentId("doc-1").fileName("policy.txt").chunkCount(2).build());
        RagServiceController controller = new RagServiceController();
        ReflectionTestUtils.setField(controller, "documentRagService", service);

        Response<IndexDocumentResponseDTO> response = controller.indexDocument("doc-1",
                new MockMultipartFile("file", "policy.txt", "text/plain", "test".getBytes()), "admin");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals("doc-1", response.getData().getDocumentId());
        Assert.assertEquals(Integer.valueOf(2), response.getData().getChunkCount());
    }

}
