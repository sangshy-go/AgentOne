package com.agentone.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent-Knowledge 绑定 VO
 */
@Data
public class AgentKnowledgeBindingVO {

    private String id;
    private String agentId;
    private String knowledgeId;
    private String knowledgeName;
    private Integer topK;
    private Double similarityThreshold;
    private LocalDateTime createdAt;
}
