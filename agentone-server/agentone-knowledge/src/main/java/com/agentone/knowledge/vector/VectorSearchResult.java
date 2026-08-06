package com.agentone.knowledge.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量检索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResult {

    /** 分块 ID */
    private String chunkId;

    /** 相似度分数（0-1，1 为最相似） */
    private Double score;
}
