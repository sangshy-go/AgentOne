package com.agentone.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模型 DTO
 */
@Data
public class ModelDTO {

    @NotBlank(message = "模型类型不能为空")
    private String modelType;  // 'chat' | 'embedding' | 'rerank' | 'image2text'

    @NotBlank(message = "模型 ID 不能为空")
    private String modelId;  // API 调用时的 model 字段值

    private String displayName;  // 显示名称

    private Integer contextSize = 4096;  // 上下文长度

    private Integer maxTokens = 2048;  // 最大响应长度（仅 chat）

    private Integer dimensions;  // 向量维度（仅 embedding 类型，由系统首次使用时自动探测，无需用户填写）
}
