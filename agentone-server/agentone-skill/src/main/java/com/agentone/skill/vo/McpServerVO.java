package com.agentone.skill.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 展示对象
 */
@Data
public class McpServerVO {

    private String id;
    private String workspaceId;
    private String name;
    private String description;
    private String transport;
    private String url;
    private String command;
    private List<String> args;
    private Map<String, String> headers;
    private Integer timeoutMs;
    private String status;
    private LocalDateTime lastConnectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 当前连接是否处于活动状态（内存连接管理器中存活） */
    private boolean connected;
    /** 已发现并注册的工具数 */
    private int toolCount;
}
