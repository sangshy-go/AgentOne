package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具执行器（课题④）：把一个已发现的 MCP 工具适配为 SkillExecutor。
 *
 * 由 McpServerServiceImpl.connect 在发现工具时逐个构建并注册进 SkillRegistry，
 * 虚拟 Skill ID = "mcp-{serverId}-{toolName}"，与 builtin 一样不落 skill 表。
 * 注册后即复用全部既有链路：Agent 绑定、buildToolkit 装配、function calling、审计。
 */
@Slf4j
public class McpSkillExecutor implements SkillExecutor {

    public static final String SKILL_ID_PREFIX = "mcp-";

    private final McpClientWrapper client;
    private final McpSchema.Tool tool;
    private final String serverId;
    private final String serverName;
    private final String workspaceId;
    private final long timeoutMs;

    public McpSkillExecutor(McpClientWrapper client, McpSchema.Tool tool,
                            String serverId, String serverName,
                            String workspaceId, long timeoutMs) {
        this.client = client;
        this.tool = tool;
        this.serverId = serverId;
        this.serverName = serverName;
        this.workspaceId = workspaceId;
        this.timeoutMs = timeoutMs;
    }

    /** 虚拟 Skill ID：mcp-{serverId}-{toolName} */
    public static String skillIdOf(String serverId, String toolName) {
        return SKILL_ID_PREFIX + serverId + "-" + toolName;
    }

    /** 从虚拟 Skill ID 解析出 serverId（serverId 为 32 位无连字符 UUID，切分无歧义） */
    public static String serverIdOf(String skillId) {
        if (skillId == null || !skillId.startsWith(SKILL_ID_PREFIX)) {
            return null;
        }
        String[] parts = skillId.split("-", 3);
        return parts.length >= 2 ? parts[1] : null;
    }

    @Override
    public SkillResult execute(SkillInvocation invocation, Context context) {
        long start = System.currentTimeMillis();
        Map<String, Object> params = invocation.getParams() != null ? invocation.getParams() : Map.of();
        try {
            McpSchema.CallToolResult result = client.callTool(tool.name(), params)
                    .block(Duration.ofMillis(timeoutMs));
            long duration = System.currentTimeMillis() - start;
            if (result == null) {
                return SkillResult.failure("MCP 工具无响应", duration);
            }
            if (Boolean.TRUE.equals(result.isError())) {
                return SkillResult.failure("MCP 工具返回错误: " + extractText(result), duration);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", extractText(result));
            if (result.structuredContent() != null) {
                data.put("structuredContent", result.structuredContent());
            }
            return SkillResult.success(data, duration);
        } catch (Exception e) {
            log.warn("MCP 工具调用失败: tool={}, error={}", tool.name(), e.getMessage());
            return SkillResult.failure("MCP 工具调用失败: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    @Override
    public SkillDescriptor getDescriptor() {
        return SkillDescriptor.builder()
                .id(skillIdOf(serverId, tool.name()))
                .name(serverName + " / " + tool.name())
                .description(tool.description() != null ? tool.description()
                        : "MCP 工具（来自 " + serverName + "）")
                .type("mcp")
                .source(serverName)
                .workspaceId(workspaceId)
                .inputSchema(toJsonSchemaMap(tool.inputSchema()))
                .version("1.0.0")
                .enabled(true)
                .build();
    }

    @Override
    public boolean isAvailable() {
        return client.isInitialized();
    }

    /** McpSchema.JsonSchema → 标准 JSON Schema Map（SkillDescriptor / function calling 用） */
    private Map<String, Object> toJsonSchemaMap(McpSchema.JsonSchema jsonSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (jsonSchema == null) {
            schema.put("type", "object");
            schema.put("properties", Map.of());
            return schema;
        }
        schema.put("type", jsonSchema.type() != null ? jsonSchema.type() : "object");
        if (jsonSchema.properties() != null) {
            schema.put("properties", jsonSchema.properties());
        } else {
            schema.put("properties", Map.of());
        }
        if (jsonSchema.required() != null && !jsonSchema.required().isEmpty()) {
            schema.put("required", jsonSchema.required());
        }
        return schema;
    }

    /** 拼接结果中的文本内容（非文本内容以占位说明，避免静默丢失） */
    private String extractText(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                parts.add(textContent.text());
            } else {
                parts.add("[" + content.getClass().getSimpleName() + " 非文本内容]");
            }
        }
        return String.join("\n", parts);
    }
}
