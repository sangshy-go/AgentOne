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
    private String description;
    private String inputSchema;
    private String outputSchema;
    private String config;
    private String version;
    private String status;
    private LocalDateTime installedAt;
}
