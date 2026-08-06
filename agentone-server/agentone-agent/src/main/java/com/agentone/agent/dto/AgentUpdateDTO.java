package com.agentone.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 更新 DTO（部分更新：所有字段可选，仅校验长度）
 */
@Data
public class AgentUpdateDTO {

    @Size(max = 100, message = "名称最长 100 字")
    private String name;

    @Size(max = 500, message = "描述最长 500 字")
    private String description;

    private String category;

    /** Agent 图标 */
    private String icon;

    /** 头像 URL */
    private String avatarUrl;

    /** AGENTS.md 源码 */
    private String agentsMd;

    /** 模型配置 JSON */
    private String modelConfig;

    /** 模型供应商 ID */
    private String modelProviderId;

    /** 记忆配置 JSON */
    private String memoryConfig;

    /** 高级配置 JSON */
    private String advancedConfig;
}
