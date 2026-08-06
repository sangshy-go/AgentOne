package com.agentone.agent.vo;

import com.agentone.agent.enums.AgentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 列表/详情 VO
 */
@Data
public class AgentVO {

    private String id;
    private String name;
    private String description;
    private String category;
    private AgentStatus status;
    private String icon;
    private String avatarUrl;
    private Integer currentVersion;
    private String agentsMd;
    private String modelConfig;
    private String modelProviderId;
    private String memoryConfig;
    private String advancedConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
