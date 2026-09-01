package com.agentone.skill.vo;

import lombok.Data;

/**
 * Skill 导出结构（发布/分享的最小形态：JSON 文件在组织内流转）。
 * 导入时反序列化为 SkillImportDTO，format 用于识别文件来源。
 */
@Data
public class SkillExportVO {

    /** 固定 agentone-skill */
    private String format;

    /** 导出结构版本，当前 1 */
    private Integer formatVersion;

    private String name;
    private String type;
    private String category;
    private String description;
    private String config;
    private String inputSchema;
    private String outputSchema;
    private String version;

    /** 导出时间（ISO，仅展示用） */
    private String exportedAt;
}
