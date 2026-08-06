package com.agentone.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档 VO
 */
@Data
public class DocumentVO {

    private String id;
    private String knowledgeId;
    private String name;
    private String type;
    private Long size;
    private Integer chunkCount;
    private String status;      // pending / processing / ready / error
    private String errorMsg;
    private LocalDateTime createdAt;
}
