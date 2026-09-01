package com.agentone.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控-会话列表 VO（全工作空间视角，含归属信息）
 */
@Data
public class MonitorSessionVO {

    private String id;
    private String title;
    private String agentId;
    private String agentName;
    private String userEmail;
    private Integer messageCount;
    private Long tokenCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
