package com.agentone.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体
 */
@Data
@TableName("document")
public class DocumentDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String knowledgeId;
    private String name;
    private String type;        // pdf / docx / md / txt / html / csv
    private Long size;
    private Integer chunkCount;
    private String status;      // pending / processing / ready / error
    private String filePath;
    private String errorMsg;
    /** 解析后的原始文本（分块前），用于失败重试和重新分块 */
    private String rawContent;
    private LocalDateTime createdAt;
}
