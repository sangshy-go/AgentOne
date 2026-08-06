package com.agentone.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库 VO
 */
@Data
public class KnowledgeBaseVO {

    private String id;
    private String workspaceId;
    private String name;
    private String description;
    private Integer docCount;
    private Integer chunkCount;
    private String embeddingModel;
    private String embeddingModelId;
    private String modelProviderId;
    private String chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private LocalDateTime createdAt;
}
