package com.agentone.skill.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent-Skill 绑定 VO
 */
@Data
public class AgentSkillBindingVO {

    private String id;
    private String agentId;
    private String skillId;
    private String skillName;
    private String skillType;
    private String skillVersion;
    private String configOverride;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
