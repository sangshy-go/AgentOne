package com.agentone.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息 VO
 */
@Data
public class ChatMessageVO {

    private String id;
    private String role;       // user / assistant / system / tool
    private String content;
    private Integer tokenCount;
    private String skillCalls;  // JSON: Skill 调用详情
    private Integer durationMs;
    private String traceId;
    private LocalDateTime createdAt;
}
