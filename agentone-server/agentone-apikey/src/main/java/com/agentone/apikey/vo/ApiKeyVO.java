package com.agentone.apikey.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 列表/详情 VO
 */
@Data
public class ApiKeyVO {

    private String id;
    private String keyPrefix;
    private String env;
    private String status;
    private List<String> allowedAgents;
    private Integer dailyLimit;
    private LocalDateTime createdAt;
}
