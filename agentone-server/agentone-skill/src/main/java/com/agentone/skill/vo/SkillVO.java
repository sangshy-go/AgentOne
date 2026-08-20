package com.agentone.skill.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Skill VO
 */
@Data
public class SkillVO {

    private String id;
    private String workspaceId;
    private String name;
    private String type;
    private String source;
    private String category;
    private String description;
    private String inputSchema;
    private String outputSchema;
    private String config;
    private String version;
    private String status;
    private LocalDateTime installedAt;

    /** Skill 中心 v2：使用次数（skill_call_log 真实调用计数） */
    private Long callCount;
    /** Skill 中心 v2：动作型技能（执行需二次确认） */
    private Boolean actionType;
    /** Skill 中心 v2：MCP 工具是否已发布到广场（非 MCP 技能为 null） */
    private Boolean published;
}
