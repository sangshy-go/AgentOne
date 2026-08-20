package com.agentone.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 工具发布治理实体（Skill 中心 v2）。
 *
 * MCP 工具是虚拟 Skill（不落 skill 表），其"发布到广场"状态独立存储。
 * 默认 published=false：IT 显式发布后才在广场可见、才可被 Agent 绑定。
 */
@Data
@TableName("mcp_tool_publish")
public class McpToolPublishDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String serverId;
    private String toolName;
    private Boolean published;
    private LocalDateTime updatedAt;
}
