package com.agentone.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型供应商 VO（API Key 脱敏显示）
 */
@Data
public class ModelProviderVO {

    private String id;
    private String workspaceId;
    private String name;
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
