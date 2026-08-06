package com.agentone.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表 VO
 */
@Data
public class ChatSessionVO {

    private String id;
    private String agentId;
    private String title;
    private Long tokenCount;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
