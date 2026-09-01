package com.agentone.skill.vo;

import lombok.Data;

/**
 * 调试向导第 1 步：可调试的 Skill 目标
 */
@Data
public class DebugTargetVO {

    private String id;
    private String name;
    /** builtin / api / mcp */
    private String type;
    private String description;
    /** JSON Schema 字符串 */
    private String inputSchema;
}
