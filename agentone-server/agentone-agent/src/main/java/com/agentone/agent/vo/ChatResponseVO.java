package com.agentone.agent.vo;

import lombok.Data;

/**
 * 同步对话响应 VO
 */
@Data
public class ChatResponseVO {

    private String sessionId;
    private String reply;
    private Integer tokenCount;
    private Integer durationMs;
    private String traceId;
}
