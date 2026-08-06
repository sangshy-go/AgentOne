package com.agentone.agent.entity;

import com.agentone.agent.enums.AgentStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 实体
 */
@Data
@TableName("agent")
public class AgentDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String name;
    private String description;
    private String category;
    private AgentStatus status;

    /** AGENTS.md 源码 */
    private String agentsMd;

    /** JSONB - 模型配置 */
    private String modelConfig;

    /** 关联的模型供应商 ID */
    private String modelProviderId;

    /** JSONB - 记忆策略配置 */
    private String memoryConfig;

    /** JSONB - 高级配置（预留字段，当前未使用） */
    private String advancedConfig;

    private String icon;
    private String avatarUrl;
    private Integer currentVersion;
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
