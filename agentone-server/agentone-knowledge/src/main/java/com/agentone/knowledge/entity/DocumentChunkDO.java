package com.agentone.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档分块实体
 */
@Data
@TableName("document_chunk")
public class DocumentChunkDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String documentId;
    private String content;
    private Integer chunkIndex;
    private String metadata;    // JSONB
}
