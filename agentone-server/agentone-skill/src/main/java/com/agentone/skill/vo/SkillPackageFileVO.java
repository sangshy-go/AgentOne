package com.agentone.skill.vo;

import lombok.Data;

/**
 * 技能包文件 VO（详情抽屉文件树 / 导入结果展示）
 */
@Data
public class SkillPackageFileVO {

    /** 包内相对路径，如 SKILL.md / scripts/scan.py */
    private String path;
    /** script / resource / doc */
    private String kind;
    private Long size;
    /** 文本内容；列表接口默认不带，单文件查看时返回 */
    private String content;
}
