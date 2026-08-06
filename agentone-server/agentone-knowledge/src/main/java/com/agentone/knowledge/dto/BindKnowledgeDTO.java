package com.agentone.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 绑定知识库 DTO
 */
@Data
public class BindKnowledgeDTO {

    @NotBlank(message = "Agent ID 不能为空")
    private String agentId;

    @NotBlank(message = "知识库 ID 不能为空")
    private String knowledgeId;

    /** 检索数量（默认 5） */
    private Integer topK;

    /** 相似度阈值（默认 0.7） */
    private Double similarityThreshold;
}
