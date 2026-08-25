package com.agentone.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话附件实体
 */
@Data
@TableName("chat_attachment")
public class ChatAttachmentDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String userId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String kind;
    private byte[] data;
    private String parsedText;
    private LocalDateTime createdAt;
}
