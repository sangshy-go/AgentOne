package com.agentone.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建 / 更新用户 Skill 的入参。
 *
 * type=api（默认，HTTP API 封装）config 示例：
 * {"url": "https://api.example.com/query", "method": "POST",
 *  "headers": {"Authorization": "Bearer xxx"}, "timeout": 10000}
 *
 * type=prompt（内容型 Skill）config 示例：
 * {"content": "## 信贷审批报告规范\n1. ..."}
 */
@Data
public class SkillDTO {

    @NotBlank(message = "Skill 名称不能为空")
    private String name;

    /** Skill 类型：api（HTTP 封装，缺省）/ prompt（内容型指令） */
    private String type;

    /** 业务分类（受控词表，缺省"其他"） */
    private String category;

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
