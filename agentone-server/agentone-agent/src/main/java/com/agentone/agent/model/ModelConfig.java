package com.agentone.agent.model;

import lombok.Data;

/**
 * 模型配置（从 agent.model_config JSON 反序列化）
 */
@Data
public class ModelConfig {

    /** 关联的 model 表记录 ID（前端「模型策略」tab 选择的 Chat 模型，优先于 model/apiKey/baseUrl） */
    private String chatModelId;

    /** 模型名称，如 gpt-4o, deepseek-chat, qwen-max */
    private String model = "gpt-4o-mini";

    /** 温度 0-2 */
    private Double temperature = 0.7;

    /** Top-P 采样 */
    private Double topP = 1.0;

    /** 最大输出 Token */
    private Integer maxTokens = 4096;

    /** 频率惩罚 -2 到 2 */
    private Double frequencyPenalty = 0.0;

    /** 是否流式输出 */
    private Boolean stream = true;

    /** 是否 JSON 模式 */
    private Boolean jsonMode = false;

    /** API Key（覆盖全局配置） */
    private String apiKey;

    /** Base URL（覆盖全局配置） */
    private String baseUrl;
}
