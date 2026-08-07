package com.agentone.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建 / 更新 API 模式 Skill 的入参。
 *
 * config 示例：
 * {"url": "https://api.example.com/query", "method": "POST",
 *  "headers": {"Authorization": "Bearer xxx"}, "timeout": 10000}
 */
@Data
public class SkillDTO {

    @NotBlank(message = "Skill 名称不能为空")
    private String name;

    private String description;

    /** 输入参数 JSON Schema（供 LLM function calling 生成参数） */
    private String inputSchema;

    /** 输出结果 JSON Schema（可选） */
    private String outputSchema;

    /** API 调用配置（JSON，必须包含 url） */
    @NotBlank(message = "Skill 配置不能为空")
    private String config;

    /** 版本号，默认 1.0.0 */
    private String version;
}
