package cn.bugstack.ai.api;

import cn.bugstack.ai.api.dto.IndexDocumentResponseDTO;
import cn.bugstack.ai.api.dto.RagAnswerRequestDTO;
import cn.bugstack.ai.api.dto.RagAnswerResponseDTO;
import cn.bugstack.ai.api.dto.SimilarChunkResponseDTO;
import cn.bugstack.ai.api.dto.SimilaritySearchRequestDTO;
import cn.bugstack.ai.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG 文档索引与检索服务。
 *
 * @author xiaoxin
 */
public interface IRagService {

    Response<IndexDocumentResponseDTO> indexDocument(String documentId,
                                                      MultipartFile file);

    Response<List<SimilarChunkResponseDTO>> similaritySearch(
            SimilaritySearchRequestDTO requestDTO);

    Response<RagAnswerResponseDTO> answer(RagAnswerRequestDTO requestDTO);

}
