package com.agentone.skill.vo;

import lombok.Data;

/**
 * MCP Server 发现的单个工具
 */
@Data
public class McpToolVO {

    /** 注册进 Registry 的虚拟 Skill ID：mcp-{serverId}-{toolName} */
    private String skillId;
    /** MCP 协议原始工具名 */
    private String toolName;
    private String description;
    /** JSON Schema 字符串 */
    private String inputSchema;
    /** Skill 中心 v2：是否已发布到广场（默认 false，IT 显式发布后才可见/可绑定） */
    private Boolean published;
    /** Skill 中心 v2：动作型标记（MCP 工具默认有副作用，执行需确认） */
    private Boolean actionType;
}
