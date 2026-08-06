package com.agentone.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建 API Key 请求
 */
@Data
public class CreateApiKeyDTO {

    /** live / test */
    @NotBlank(message = "env 不能为空")
    private String env;

    /** 允许调用的 Agent ID 列表，空表示全部 */
    private List<String> allowedAgents;

    /** 每日调用上限，默认 1000 */
    private Integer dailyLimit;
}
