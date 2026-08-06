package com.agentone.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBaseDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String name;
    private String description;
    private Integer docCount;
    private Integer chunkCount;
    private String embeddingModel;  // 保留作为 fallback（向下兼容）
    private String embeddingModelId; // 绑定 embedding 模型（用于向量化）
    private String modelProviderId;  // 关联模型供应商（用于动态 Embedding/Chat）

    /** 分块策略: by-length（按长度）/ by-title（按标题）/ by-paragraph（按段落） */
    private String chunkStrategy;
    /** 每块最大字符数（by-length / by-title 超长回退使用） */
    private Integer chunkSize;
    /** 相邻块重叠字符数 */
    private Integer chunkOverlap;

    private LocalDateTime createdAt;
}
