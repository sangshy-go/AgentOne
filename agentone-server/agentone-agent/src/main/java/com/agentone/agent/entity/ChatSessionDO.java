package com.agentone.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体
 */
@Data
@TableName("chat_session")
public class ChatSessionDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String agentId;
    private String userId;
    private String title;
    private Long tokenCount;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
