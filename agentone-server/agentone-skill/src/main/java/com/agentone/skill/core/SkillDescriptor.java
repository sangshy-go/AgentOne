package com.agentone.skill.core;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Skill 描述符
 * 统一描述一个 Skill 的元信息
 */
@Data
@Builder
public class SkillDescriptor {

    /** Skill 唯一标识 */
    private String id;

    /** Skill 名称 */
    private String name;

    /** Skill 描述 */
    private String description;

    /** Skill 类型: builtin / api / function / mcp */
    private String type;

    /** 输入参数 Schema (JSON Schema 格式) */
    private Map<String, Object> inputSchema;

    /** 输出结果 Schema */
    private Map<String, Object> outputSchema;

    /** 版本号 */
    private String version;

    /** 来源标识 */
    private String source;

    /** 是否启用 */
    private boolean enabled;
}
