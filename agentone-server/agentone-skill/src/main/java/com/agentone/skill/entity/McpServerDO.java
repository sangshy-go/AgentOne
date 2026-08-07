package com.agentone.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP Server 实体（课题④）。
 *
 * transport 决定连接方式：
 * - stdio：本地子进程，走 command + args
 * - sse / streamable_http：远程，走 url + headers
 */
@Data
@TableName("mcp_server")
public class McpServerDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String name;
    private String description;
    private String transport;      // stdio / sse / streamable_http
    private String url;            // sse / streamable_http
    private String command;        // stdio
    private String args;           // JSONB，字符串数组
    private String headers;        // JSONB，键值对
    private Integer timeoutMs;
    private String status;         // active / disabled
    private LocalDateTime lastConnectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
