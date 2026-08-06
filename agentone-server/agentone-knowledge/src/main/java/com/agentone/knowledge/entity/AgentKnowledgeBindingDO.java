package com.agentone.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent-Knowledge 绑定实体
 */
@Data
@TableName("agent_knowledge_binding")
public class AgentKnowledgeBindingDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属工作空间（S3：纳入多租户隔离，防止跨租户 RAG 泄露与越权解绑） */
    private String workspaceId;

    private String agentId;
    private String knowledgeId;
    private Integer topK;
    private Double similarityThreshold;
    private LocalDateTime createdAt;
}
