package com.agentone.agent.vo;

import lombok.Data;

/**
 * 模型检测结果 VO
 */
@Data
public class ModelCheckVO {

    private boolean available;
    private String model;
    private String message;
    private Long latencyMs;
}
