package com.agentone.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Skill 导入入参（导出文件反序列化的结果）。
 * 字段与 SkillDTO 对齐，额外携带 format 标记以识别非 AgentOne 导出文件。
 */
@Data
public class SkillImportDTO {

    /** 导出格式标记：agentone-skill（导出时写入；手写字段可缺省） */
    private String format;

    @NotBlank(message = "Skill 名称不能为空")
    private String name;

    /** Skill 类型：api / prompt（缺省 api） */
    private String type;

    /** 业务分类（缺省"其他"） */
    private String category;

    private String description;

    private String inputSchema;

    private String outputSchema;

    @NotBlank(message = "Skill 配置不能为空")
    private String config;

    private String version;
}
