package cn.bugstack.ai.api.dto;

import lombok.Data;

import java.util.List;

/**
 * RAG 问答响应。
 *
 * @author xiaoxin
 */
@Data
public class RagAnswerResponseDTO {

    private String query;

    private String answer;

    private List<SimilarChunkResponseDTO> sources;

}
