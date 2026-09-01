package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * McpSkillExecutor 单测（课题④）：
 * 虚拟 Skill ID 编解码、描述符装配、MCP 调用结果到 SkillResult 的适配。
 * McpClientWrapper 为抽象类，直接 mock，不发真实连接。
 */
@ExtendWith(MockitoExtension.class)
class McpSkillExecutorTest {

    @Mock
    private McpClientWrapper client;

    private McpSchema.Tool tool;
    private McpSkillExecutor executor;

    @BeforeEach
    void setUp() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object",
                Map.of("city", Map.of("type", "string")),
                List.of("city"),
                null, null, null);
        tool = new McpSchema.Tool("get_weather", null, "查询城市天气", schema, null, null, null);
        executor = new McpSkillExecutor(client, tool, "serverX", "天气服务", "ws-1", 5000L);
    }

    private SkillInvocation invocation(Map<String, Object> params) {
        return SkillInvocation.builder()
                .skillId("mcp-serverX-get_weather")
                .params(params)
                .traceId("trace-1")
                .build();
    }

    @Test
    void skillIdOf_joinsServerAndTool() {
        assertEquals("mcp-serverX-get_weather", McpSkillExecutor.skillIdOf("serverX", "get_weather"));
    }

    @Test
    void serverIdOf_roundTrips() {
        assertEquals("serverX", McpSkillExecutor.serverIdOf("mcp-serverX-get_weather"));
    }

    @Test
    void serverIdOf_toolNameWithDashes_stillParses() {
        // toolName 含连字符时，split limit=3 保证 serverId 切分无歧义
        assertEquals("serverX", McpSkillExecutor.serverIdOf("mcp-serverX-a-b-c"));
    }

    @Test
    void serverIdOf_nonMcpId_returnsNull() {
        assertNull(McpSkillExecutor.serverIdOf("builtin-http-request"));
        assertNull(McpSkillExecutor.serverIdOf(null));
    }

    @Test
    void getDescriptor_exposesMcpTypeAndWorkspace() {
        SkillDescriptor d = executor.getDescriptor();
        assertEquals("mcp-serverX-get_weather", d.getId());
        assertEquals("天气服务 / get_weather", d.getName());
        assertEquals("查询城市天气", d.getDescription());
        assertEquals("mcp", d.getType());
        assertEquals("天气服务", d.getSource());
        assertEquals("ws-1", d.getWorkspaceId(), "描述符必须携带归属空间供租户过滤");
        assertEquals("object", d.getInputSchema().get("type"));
        assertTrue(d.isEnabled());
    }

    @Test
    void getDescriptor_nullToolSchema_fallsBackToEmptyObject() {
        McpSchema.Tool noSchema = new McpSchema.Tool("t", null, null, null, null, null, null);
        McpSkillExecutor ex = new McpSkillExecutor(client, noSchema, "s", "S", "ws-1", 1000L);
        SkillDescriptor d = ex.getDescriptor();
        assertEquals("object", d.getInputSchema().get("type"));
        assertEquals(Map.of(), d.getInputSchema().get("properties"));
        assertTrue(d.getDescription().contains("S"), "无描述时降级文案应包含来源名");
    }

    @Test
    void execute_success_returnsTextAndStructured() {
        McpSchema.CallToolResult ok = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("北京晴")), false);
        when(client.callTool(eq("get_weather"), any())).thenReturn(Mono.just(ok));

        SkillResult result = executor.execute(invocation(Map.of("city", "北京")),
                Context.of("user-1", "ws-1"));

        assertTrue(result.isSuccess());
        assertEquals("北京晴", result.getData().get("text"));
        assertNull(result.getData().get("structuredContent"));
    }

    @Test
    void execute_toolReturnsError_failureWithText() {
        McpSchema.CallToolResult err = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("城市不存在")), true);
        when(client.callTool(eq("get_weather"), any())).thenReturn(Mono.just(err));

        SkillResult result = executor.execute(invocation(Map.of()), Context.of("u", "ws-1"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("城市不存在"));
    }

    @Test
    void execute_nullResult_failure() {
        when(client.callTool(eq("get_weather"), any())).thenReturn(Mono.empty());

        SkillResult result = executor.execute(invocation(Map.of()), Context.of("u", "ws-1"));

        assertFalse(result.isSuccess());
        assertEquals("MCP 工具无响应", result.getErrorMessage());
    }

    @Test
    void execute_clientThrows_failureNotException() {
        when(client.callTool(eq("get_weather"), any()))
                .thenThrow(new RuntimeException("connection lost"));

        SkillResult result = executor.execute(invocation(Map.of()), Context.of("u", "ws-1"));

        assertFalse(result.isSuccess(), "调用异常应转为失败结果而不是抛出");
        assertTrue(result.getErrorMessage().contains("connection lost"));
    }

    @Test
    void execute_nullParams_treatedAsEmpty() {
        McpSchema.CallToolResult ok = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("ok")), false);
        when(client.callTool(eq("get_weather"), any())).thenReturn(Mono.just(ok));

        SkillResult result = executor.execute(invocation(null), Context.of("u", "ws-1"));

        assertTrue(result.isSuccess());
    }

    @Test
    void isAvailable_delegatesToClient() {
        when(client.isInitialized()).thenReturn(true);
        assertTrue(executor.isAvailable());
    }
}
