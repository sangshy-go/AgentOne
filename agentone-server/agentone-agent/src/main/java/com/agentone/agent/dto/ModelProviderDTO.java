package com.agentone.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型供应商 DTO
 *
 * 注意：所有字段均不加 @NotBlank，因为 update 场景下字段可选。
 * create 时的必填校验在 Service 层手动完成。
 */
@Data
public class ModelProviderDTO {

    @Size(max = 100, message = "名称不能超过100个字符")
    private String name;

    private String provider;

    @Size(max = 500, message = "API Key 过长")
    private String apiKey;

    @Size(max = 500, message = "Base URL 过长")
    private String baseUrl;
}
