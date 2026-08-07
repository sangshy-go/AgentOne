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

    /**
     * 归属工作空间。
     * builtin 为 null（全局可见）；api / mcp 为所属工作空间，
     * 列表合并/绑定/调试目标列举/直接测试时据此做租户过滤，防止跨租户泄露。
     */
    private String workspaceId;

    /** 是否启用 */
    private boolean enabled;
}
