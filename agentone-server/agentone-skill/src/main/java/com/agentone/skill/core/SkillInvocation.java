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

    /**
     * 本次调用的超时时间（毫秒）。
     * 执行器优先采用此值，为空时回落到 Skill 自身配置 / 执行器默认值。
     */
    private Long timeoutMs;

    /** 调用追踪 ID */
    private String traceId;

    /**
     * 配置覆盖（Agent 绑定 Skill 时可设置的 configOverride）。
     * 执行器把它与 Skill 自身 config 深合并后作为本次执行的生效配置（覆盖侧优先），
     * 为空表示完全使用 Skill 自身配置。
     */
    private Map<String, Object> configOverride;
}
