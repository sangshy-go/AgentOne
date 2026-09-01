package com.agentone.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控-Skill 调用记录 VO（不含大体积的入参/出参 JSON）
 */
@Data
public class SkillCallVO {

    private String id;
    private String agentId;
    private String agentName;
    private String skillId;
    private String skillName;
    private String sessionId;
    private String status;
    private String errorMessage;
    private Long durationMs;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
