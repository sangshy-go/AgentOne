package com.agentone.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型 VO
 */
@Data
public class ModelVO {

    private String id;
    private String providerId;
    private String providerName;  // 冗余字段，方便前端展示
    private String modelType;
    private String modelId;
    private String displayName;
    private Integer contextSize;
    private Integer maxTokens;
    private Integer dimensions;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
