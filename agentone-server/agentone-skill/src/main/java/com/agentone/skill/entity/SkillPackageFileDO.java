package com.agentone.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技能包文件实体（Skill 中心 v2）。
 *
 * 脚本包 / 导入技能包的文件存储：SKILL.md + scripts + resources。
 * 脚本本期仅存储与随包分发，不执行（安全红线，沙箱另立专项）。
 */
@Data
@TableName("skill_package_file")
public class SkillPackageFileDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String skillId;
    private String path;        // 包内相对路径，如 SKILL.md / scripts/scan.py
    private String kind;        // script / resource / doc
    private Long size;
    private String content;     // 文本内容；二进制资源为 NULL
    private LocalDateTime createdAt;
}
