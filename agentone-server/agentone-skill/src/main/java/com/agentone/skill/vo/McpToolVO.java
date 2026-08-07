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
}
