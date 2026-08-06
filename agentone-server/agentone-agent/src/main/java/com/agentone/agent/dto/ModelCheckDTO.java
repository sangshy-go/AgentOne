package com.agentone.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模型可用性检测请求
 */
@Data
public class ModelCheckDTO {

    @NotBlank(message = "模型名称不能为空")
    private String model;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    /** Base URL（可选，默认 OpenAI） */
    private String baseUrl;
}
