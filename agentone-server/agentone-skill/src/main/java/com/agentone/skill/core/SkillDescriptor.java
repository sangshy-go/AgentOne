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

    /** Skill 类型: builtin / api / prompt / function / mcp */
    private String type;

    /** 业务分类（课题⑧，供列表筛选；虚拟挂载技能取固定值，见 SkillCategories） */
    private String category;

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

    /**
     * 动作型标记（Skill 中心 v2 治理）：有副作用的技能（发邮件/写数据/调外部系统）。
     * 用户 Skill 由 config.actionType 驱动；MCP 工具默认 true（SDK 暂无注解可读，保守处理）。
     * 动作型技能执行走「草稿→确认→执行」两阶段并写审计。
     */
    private boolean actionType;
}
