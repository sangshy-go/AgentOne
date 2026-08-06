package com.agentone.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库 DTO
 */
@Data
public class KnowledgeBaseDTO {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /** Embedding 模型（默认 text-embedding-3-small，向下兼容） */
    private String embeddingModel;

    /** 绑定的 embedding 模型 ID（来自 model 表） */
    private String embeddingModelId;

    /** 分块策略（by-length / by-title / by-paragraph），默认 by-length */
    private String chunkStrategy;

    /** 每块最大字符数，默认 400 */
    private Integer chunkSize;

    /** 相邻块重叠字符数，默认 60 */
    private Integer chunkOverlap;
}
