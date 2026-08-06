package com.agentone.skill.core;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Skill 调用参数
 */
@Data
@Builder
public class SkillInvocation {

    /** Skill ID */
    private String skillId;

    /** 调用参数 */
    private Map<String, Object> params;

    /** 超时时间（毫秒） */
    private Long timeoutMs;

    /** 调用追踪 ID */
    private String traceId;

    /**
     * TODO: Phase 2 实现 - Skill 配置覆盖
     * 当前 Agent 绑定 Skill 时可以设置 configOverride，但执行时未生效。
     * 需要在此字段传递覆盖配置，并在 SkillExecutor 中合并到默认配置。
     */
    // private Map<String, Object> configOverride;
}
