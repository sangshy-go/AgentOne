package com.agentone.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 / 更新 MCP Server 入参
 */
@Data
public class McpServerDTO {

    @NotBlank(message = "name 不能为空")
    private String name;

    private String description;

    /** stdio / sse / streamable_http */
    @NotBlank(message = "transport 不能为空")
    private String transport;

    /** sse / streamable_http 的目标地址 */
    private String url;

    /** stdio 的启动命令 */
    private String command;

    /** stdio 命令参数 */
    private List<String> args;

    /** sse / streamable_http 自定义请求头 */
    private Map<String, String> headers;

    /** 调用超时（毫秒），缺省 30000 */
    private Integer timeoutMs;

    /** active / disabled，缺省 active */
    private String status;
}
