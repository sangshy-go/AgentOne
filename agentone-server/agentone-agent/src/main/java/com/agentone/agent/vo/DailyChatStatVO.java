package com.agentone.agent.vo;

import lombok.Data;

/**
 * 按天聚合的会话统计（mapUnderscoreToCamelCase 映射 stat_date/stat_count）
 */
@Data
public class DailyChatStatVO {

    private String statDate;
    private Long statCount;
}
