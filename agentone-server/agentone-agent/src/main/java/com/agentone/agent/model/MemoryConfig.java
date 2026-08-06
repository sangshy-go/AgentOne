package com.agentone.agent.model;

import lombok.Data;

/**
 * 记忆策略配置
 */
@Data
public class MemoryConfig {

    /** 预设档位: light / standard / deep / custom */
    private String preset = "standard";

    /** 短期记忆轮数（滑动窗口） */
    private Integer shortTermRounds = 10;

    /** 长期记忆开关 */
    private Boolean longTermEnabled = false;

    /** 最大 Token 窗口 */
    private Integer maxTokenWindow = 8000;

    /** Token 超限策略: sliding_window / summary */
    private String overflowStrategy = "sliding_window";
}
