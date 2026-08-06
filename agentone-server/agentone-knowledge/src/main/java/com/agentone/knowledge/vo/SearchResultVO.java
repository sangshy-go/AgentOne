package com.agentone.knowledge.vo;

import lombok.Data;

/**
 * 检索结果 VO
 */
@Data
public class SearchResultVO {

    private String chunkId;
    private String documentId;
    private String documentName;
    private String content;
    private Double score;       // 相似度分数
    private Integer chunkIndex;
}
