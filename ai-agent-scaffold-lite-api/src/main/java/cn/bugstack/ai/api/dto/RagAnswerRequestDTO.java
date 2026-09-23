package cn.bugstack.ai.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * RAG 问答请求。
 *
 * @author xiaoxin
 */
@Data
public class RagAnswerRequestDTO {

    @NotBlank
    private String query;

    @Min(1)
    @Max(50)
    private Integer topK;

    @DecimalMin("-1.0")
    @DecimalMax("1.0")
    private Double minSimilarity;

    private List<String> documentIds;

}
