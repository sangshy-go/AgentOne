package com.agentone.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体
 */
@Data
@TableName("chat_message")
public class ChatMessageDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;
    private String role;       // user / assistant / system / tool
    private String content;
    private Integer tokenCount;
    private String skillCalls;  // JSONB
    private String attachments; // JSONB
    private Integer durationMs;
    private String traceId;
    private LocalDateTime createdAt;
}
