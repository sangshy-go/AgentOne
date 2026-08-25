package com.agentone.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 附件元信息 VO（不含二进制 data）
 */
@Data
public class AttachmentVO {

    private String id;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String kind;
    private LocalDateTime createdAt;
}
