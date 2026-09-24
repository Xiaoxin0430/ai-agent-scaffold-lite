package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IRagService;
import cn.bugstack.ai.api.dto.IndexDocumentResponseDTO;
import cn.bugstack.ai.api.dto.RagAnswerRequestDTO;
import cn.bugstack.ai.api.dto.RagAnswerResponseDTO;
import cn.bugstack.ai.api.dto.SimilarChunkResponseDTO;
import cn.bugstack.ai.api.dto.SimilaritySearchRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.valobj.DocumentIndexResultVO;
import cn.bugstack.ai.domain.rag.model.valobj.RagAnswerVO;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.domain.rag.service.IDocumentRagService;
import cn.bugstack.ai.types.enums.ResponseCode;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.List;

/**
 * RAG 文档索引与检索接口。
 *
 * @author xiaoxin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@CrossOrigin(origins = "*")
public class RagServiceController implements IRagService {

    @Resource
    private IDocumentRagService documentRagService;

    @Override
    @PostMapping(value = "/documents/{documentId}/index", consumes = "multipart/form-data")
    public Response<IndexDocumentResponseDTO> indexDocument(
            @PathVariable("documentId") String documentId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("userId") String userId) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("file cannot be empty");
            }
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId cannot be blank");
            }
            String fileName = file.getOriginalFilename();
            try (InputStream inputStream = file.getInputStream()) {
                DocumentIndexResultVO result = documentRagService.indexDocument(
                        documentId, fileName, inputStream, userId);
                log.info("文档索引完成 documentId:{} fileName:{} chunkCount:{}",
                        result.getDocumentId(), result.getFileName(), result.getChunkCount());
                return success(toIndexResponse(result));
            }
        } catch (IllegalArgumentException e) {
            log.warn("文档索引参数错误 documentId:{} message:{}", documentId, e.getMessage());
            return failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("文档索引失败 documentId:{}", documentId, e);
            return failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo());
        }
    }

    @Override
    @PostMapping("/search")
    public Response<List<SimilarChunkResponseDTO>> similaritySearch(
            @Valid @RequestBody SimilaritySearchRequestDTO requestDTO) {
        try {
            List<SimilarChunkResponseDTO> results = documentRagService.searchSimilarChunks(
                            requestDTO.getQuery(),
                            requestDTO.getTopK(),
                            requestDTO.getMinSimilarity(),
                            requestDTO.getDocumentIds())
                    .stream()
                    .map(this::toSearchResponse)
                    .toList();
            log.info("Chunk 检索完成 topK:{} resultCount:{}",
                    requestDTO.getTopK(), results.size());
            return success(results);
        } catch (IllegalArgumentException e) {
            log.warn("Chunk 检索参数错误 message:{}", e.getMessage());
            return failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("Chunk 检索失败", e);
            return failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo());
        }
    }

    @Override
    @PostMapping("/answer")
    public Response<RagAnswerResponseDTO> answer(
            @Valid @RequestBody RagAnswerRequestDTO requestDTO) {
        try {
            RagAnswerVO result = documentRagService.answerQuestion(
                    requestDTO.getQuery(),
                    requestDTO.getTopK(),
                    requestDTO.getMinSimilarity(),
                    requestDTO.getDocumentIds());
            RagAnswerResponseDTO responseDTO = new RagAnswerResponseDTO();
            responseDTO.setQuery(result.getQuery());
            responseDTO.setAnswer(result.getAnswer());
            responseDTO.setSources(result.getSources().stream()
                    .map(this::toSearchResponse)
                    .toList());
            log.info("RAG 问答完成 sourceCount:{}", responseDTO.getSources().size());
            return success(responseDTO);
        } catch (IllegalArgumentException e) {
            log.warn("RAG 问答参数错误 message:{}", e.getMessage());
            return failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("RAG 问答失败", e);
            return failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo());
        }
    }

    private IndexDocumentResponseDTO toIndexResponse(DocumentIndexResultVO result) {
        IndexDocumentResponseDTO responseDTO = new IndexDocumentResponseDTO();
        responseDTO.setDocumentId(result.getDocumentId());
        responseDTO.setFileName(result.getFileName());
        responseDTO.setChunkCount(result.getChunkCount());
        responseDTO.setEmbeddingModel(result.getEmbeddingModel());
        responseDTO.setEmbeddingVersion(result.getEmbeddingVersion());
        responseDTO.setEmbeddingDimensions(result.getEmbeddingDimensions());
        responseDTO.setIndexedAt(result.getIndexedAt());
        return responseDTO;
    }

    private SimilarChunkResponseDTO toSearchResponse(SimilarDocumentChunkVO result) {
        DocumentChunkEntity chunk = result.getChunk();
        SimilarChunkResponseDTO responseDTO = new SimilarChunkResponseDTO();
        responseDTO.setChunkId(chunk.getChunkId());
        responseDTO.setDocumentId(chunk.getDocumentId());
        responseDTO.setFileName(result.getFileName());
        responseDTO.setChunkIndex(chunk.getChunkIndex());
        responseDTO.setContent(chunk.getContent());
        responseDTO.setTokenCount(chunk.getTokenCount());
        responseDTO.setContextStartOffset(chunk.getContextStartOffset());
        responseDTO.setCoreStartOffset(chunk.getCoreStartOffset());
        responseDTO.setCoreEndOffset(chunk.getCoreEndOffset());
        responseDTO.setCleaningVersion(chunk.getCleaningVersion());
        responseDTO.setChunkingVersion(chunk.getChunkingVersion());
        responseDTO.setEmbeddingModel(result.getEmbeddingModel());
        responseDTO.setEmbeddingVersion(result.getEmbeddingVersion());
        responseDTO.setEmbeddingDimensions(result.getEmbeddingDimensions());
        responseDTO.setSimilarity(result.getSimilarity());
        responseDTO.setMetadata(chunk.getMetadata());
        return responseDTO;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> failure(ResponseCode responseCode, String message) {
        return Response.<T>builder()
                .code(responseCode.getCode())
                .info(message)
                .build();
    }

}
