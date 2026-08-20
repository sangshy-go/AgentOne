package com.agentone.skill.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.PageResult;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.dto.McpServerDTO;
import com.agentone.skill.entity.McpServerDO;
import com.agentone.skill.entity.McpToolPublishDO;
import com.agentone.skill.executor.McpSkillExecutor;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.McpServerMapper;
import com.agentone.skill.mapper.McpToolPublishMapper;
import com.agentone.skill.mcp.McpConnectionManager;
import com.agentone.skill.vo.McpServerVO;
import com.agentone.skill.vo.McpToolVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpServerServiceImpl 单测（课题④）：
 * CRUD 校验、连接生命周期、删除保护、工具注册与租户过滤。
 * Mapper / Registry / ConnectionManager 全部 mock，不依赖 DB 与真实 MCP Server。
 */
@ExtendWith(MockitoExtension.class)
class McpServerServiceImplTest {

    @Mock
    private McpServerMapper mcpServerMapper;
    @Mock
    private AgentSkillBindingMapper bindingMapper;
    @Mock
    private SkillRegistry skillRegistry;
    @Mock
    private McpConnectionManager connectionManager;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private McpToolPublishMapper mcpToolPublishMapper;

    @InjectMocks
    private McpServerServiceImpl mcpServerService;

    @BeforeEach
    void setUp() {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
        // toVO 会遍历 Registry 统计工具数，默认空集合（lenient：部分用例不用）
        lenient().when(skillRegistry.listDescriptors()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    private McpServerDTO stdioDto() {
        McpServerDTO dto = new McpServerDTO();
        dto.setName("天气 MCP");
        dto.setTransport("stdio");
        dto.setCommand("npx");
        dto.setArgs(List.of("-y", "weather-mcp"));
        return dto;
    }

    private McpServerDO server(String id, String transport) {
        McpServerDO server = new McpServerDO();
        server.setId(id);
        server.setWorkspaceId("ws-1");
        server.setName("天气 MCP");
        server.setTransport(transport);
        server.setTimeoutMs(30000);
        server.setStatus("active");
        server.setArgs("[]");
        server.setHeaders("{}");
        return server;
    }

    // ---------- create ----------

    @Test
    void create_success_appliesDefaultsAndSerializesArgs() {
        McpServerVO vo = mcpServerService.create(stdioDto());

        ArgumentCaptor<McpServerDO> captor = ArgumentCaptor.forClass(McpServerDO.class);
        verify(mcpServerMapper).insert(captor.capture());
        McpServerDO saved = captor.getValue();
        assertEquals("ws-1", saved.getWorkspaceId());
        assertEquals("active", saved.getStatus(), "未指定 status 应默认 active");
        assertEquals(30000, saved.getTimeoutMs(), "未指定超时应默认 30000");
        assertEquals("[\"-y\",\"weather-mcp\"]", saved.getArgs());
        assertNotNull(saved.getCreatedAt());

        assertEquals("天气 MCP", vo.getName());
        assertFalse(vo.isConnected());
        assertEquals(0, vo.getToolCount());
    }

    @Test
    void create_invalidTransport_throws5010() {
        McpServerDTO dto = stdioDto();
        dto.setTransport("websocket");
        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.create(dto));
        assertEquals(5010, e.getCode());
        verify(mcpServerMapper, never()).insert(any(McpServerDO.class));
    }

    @Test
    void create_stdioWithoutCommand_throws5010() {
        McpServerDTO dto = stdioDto();
        dto.setCommand("  ");
        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.create(dto));
        assertEquals(5010, e.getCode());
    }

    @Test
    void create_sseWithoutUrl_throws5010() {
        McpServerDTO dto = stdioDto();
        dto.setTransport("sse");
        dto.setCommand(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.create(dto));
        assertEquals(5010, e.getCode());
    }

    // ---------- get / ownsServer ----------

    @Test
    void get_notFound_throws5009() {
        when(mcpServerMapper.selectById("missing")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.get("missing"));
        assertEquals(5009, e.getCode());
    }

    @Test
    void ownsServer_selectByIdHitOrMiss() {
        when(mcpServerMapper.selectById("s1")).thenReturn(server("s1", "stdio"));
        when(mcpServerMapper.selectById("s2")).thenReturn(null);
        assertTrue(mcpServerService.ownsServer("s1"));
        assertFalse(mcpServerService.ownsServer("s2"));
    }

    // ---------- update ----------

    @Test
    void update_connectionChanged_closesOldConnection() {
        McpServerDO existing = server("s1", "sse");
        existing.setUrl("http://9.9.9.9/old");
        when(mcpServerMapper.selectById("s1")).thenReturn(existing);
        when(connectionManager.isConnected("s1")).thenReturn(true);

        McpServerDTO dto = stdioDto();
        dto.setTransport("sse");
        dto.setCommand(null);
        dto.setUrl("http://9.9.9.9/new");
        mcpServerService.update("s1", dto);

        verify(connectionManager).close("s1");
        verify(mcpServerMapper).updateById(existing);
        assertEquals("http://9.9.9.9/new", existing.getUrl());
    }

    @Test
    void update_sameConnectionConfig_keepsConnection() {
        McpServerDO existing = server("s1", "sse");
        existing.setUrl("http://9.9.9.9/mcp");
        when(mcpServerMapper.selectById("s1")).thenReturn(existing);

        McpServerDTO dto = stdioDto();
        dto.setTransport("sse");
        dto.setCommand(null);
        dto.setArgs(List.of()); // 连接参数（transport/url/command/args/headers/timeout）全一致才保持连接
        dto.setUrl("http://9.9.9.9/mcp");
        mcpServerService.update("s1", dto);

        verify(connectionManager, never()).close(any());
    }

    // ---------- delete ----------

    @Test
    void delete_toolsStillBound_throws5012() {
        when(mcpServerMapper.selectById("s1")).thenReturn(server("s1", "stdio"));
        when(bindingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.delete("s1"));
        assertEquals(5012, e.getCode(), "工具仍被 Agent 绑定必须拒绝删除");
        verify(mcpServerMapper, never()).deleteById(anyString());
    }

    @Test
    void delete_success_unregistersToolsAndCloses() {
        when(mcpServerMapper.selectById("s1")).thenReturn(server("s1", "stdio"));
        when(bindingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        mcpServerService.delete("s1");

        verify(connectionManager).close("s1");
        verify(mcpServerMapper).deleteById("s1");
        verify(mcpToolPublishMapper).delete(any(LambdaQueryWrapper.class));
    }

    // ---------- list ----------

    @Test
    void list_mapsRecordsToVO() {
        Page<McpServerDO> page = new Page<>(1, 20);
        page.setRecords(List.of(server("s1", "stdio")));
        page.setTotal(1);
        when(mcpServerMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<McpServerVO> result = mcpServerService.list(1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("天气 MCP", result.getRecords().get(0).getName());
    }

    // ---------- listTools ----------

    @Test
    void listTools_filtersRegistryByServerPrefix() {
        when(mcpServerMapper.selectById("s1")).thenReturn(server("s1", "stdio"));
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                SkillDescriptor.builder().id("mcp-s1-get_weather").type("mcp")
                        .description("天气").inputSchema(Map.of("type", "object")).build(),
                SkillDescriptor.builder().id("mcp-s2-other").type("mcp").build(),
                SkillDescriptor.builder().id("builtin-x").type("builtin").build()));

        List<McpToolVO> tools = mcpServerService.listTools("s1");

        assertEquals(1, tools.size());
        assertEquals("get_weather", tools.get(0).getToolName());
        assertEquals("mcp-s1-get_weather", tools.get(0).getSkillId());
    }

    @Test
    void listTools_marksPublishedFlagAndActionType() {
        when(mcpServerMapper.selectById("s1")).thenReturn(server("s1", "stdio"));
        // 真实注册时 McpSkillExecutor 描述符固定 actionType=true（SDK 无注解可读，保守处理）
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                SkillDescriptor.builder().id("mcp-s1-get_weather").type("mcp")
                        .description("天气").inputSchema(Map.of("type", "object"))
                        .actionType(true).build()));
        McpToolPublishDO pub = new McpToolPublishDO();
        pub.setServerId("s1");
        pub.setToolName("get_weather");
        pub.setPublished(true);
        when(mcpToolPublishMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pub));

        List<McpToolVO> tools = mcpServerService.listTools("s1");

        assertEquals(1, tools.size());
        assertTrue(tools.get(0).getPublished(), "已发布工具必须带出发布状态");
        assertTrue(tools.get(0).getActionType(), "MCP 工具默认动作型（SDK 无注解可读）");
    }

    // ---------- publishTool（工具级发布开关） ----------

    @Test
    void publishTool_firstTime_insertsRow() {
        when(mcpServerMapper.selectById("s1")).thenReturn(server("s1", "stdio"));
        when(mcpToolPublishMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        McpToolVO vo = mcpServerService.publishTool("s1", "get_weather", true);

        assertEquals("mcp-s1-get_weather", vo.getSkillId());
        assertTrue(vo.getPublished());
        ArgumentCaptor<McpToolPublishDO> captor = ArgumentCaptor.forClass(McpToolPublishDO.class);
        verify(mcpToolPublishMapper).insert(captor.capture());
        assertEquals("ws-1", captor.getValue().getWorkspaceId());
        assertEquals("get_weather", captor.getValue().getToolName());
        assertTrue(captor.getValue().getPublished());
    }

    @Test
    void publishTool_existingRow_updatesInsteadOfInsert() {
        when(mcpServerMapper.selectById("s1")).thenReturn(server("s1", "stdio"));
        McpToolPublishDO existing = new McpToolPublishDO();
        existing.setServerId("s1");
        existing.setToolName("get_weather");
        existing.setPublished(true);
        when(mcpToolPublishMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        McpToolVO vo = mcpServerService.publishTool("s1", "get_weather", false);

        assertFalse(vo.getPublished());
        assertFalse(existing.getPublished());
        verify(mcpToolPublishMapper).updateById(existing);
        verify(mcpToolPublishMapper, never()).insert(any(McpToolPublishDO.class));
    }

    @Test
    void publishTool_blankToolName_throws5010() {
        when(mcpServerMapper.selectById("s1")).thenReturn(server("s1", "stdio"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.publishTool("s1", "  ", true));
        assertEquals(5010, e.getCode());
    }

    @Test
    void publishTool_serverNotFound_throws5009() {
        when(mcpServerMapper.selectById("missing")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.publishTool("missing", "tool", true));
        assertEquals(5009, e.getCode());
        verify(mcpToolPublishMapper, never()).insert(any(McpToolPublishDO.class));
    }

    // ---------- connect ----------

    @Test
    void connect_success_registersToolsWithWorkspace() {
        McpServerDO existing = server("s1", "stdio");
        when(mcpServerMapper.selectById("s1")).thenReturn(existing);
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(connectionManager.open(existing)).thenReturn(client);
        McpSchema.Tool tool = new McpSchema.Tool("get_weather", null, "天气",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null),
                null, null, null);
        when(client.listTools()).thenReturn(Mono.just(List.of(tool)));

        List<McpToolVO> tools = mcpServerService.connect("s1");

        assertEquals(1, tools.size());
        assertEquals("mcp-s1-get_weather", tools.get(0).getSkillId());

        ArgumentCaptor<SkillExecutor> captor = ArgumentCaptor.forClass(SkillExecutor.class);
        verify(skillRegistry).register(captor.capture());
        assertEquals("ws-1", captor.getValue().getDescriptor().getWorkspaceId(),
                "注册的 MCP 工具必须携带归属空间");

        ArgumentCaptor<McpServerDO> updateCaptor = ArgumentCaptor.forClass(McpServerDO.class);
        verify(mcpServerMapper).updateById(updateCaptor.capture());
        assertNotNull(updateCaptor.getValue().getLastConnectedAt());
    }

    @Test
    void connect_openFails_throws5011() {
        McpServerDO existing = server("s1", "stdio");
        when(mcpServerMapper.selectById("s1")).thenReturn(existing);
        when(connectionManager.open(existing)).thenThrow(new RuntimeException("connection refused"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.connect("s1"));
        assertEquals(5011, e.getCode());
        assertTrue(e.getMessage().contains("connection refused"));
        verify(skillRegistry, never()).register(any());
    }

    @Test
    void connect_toolDiscoveryFails_closesConnectionAndThrows5011() {
        McpServerDO existing = server("s1", "stdio");
        when(mcpServerMapper.selectById("s1")).thenReturn(existing);
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(connectionManager.open(existing)).thenReturn(client);
        when(client.listTools()).thenThrow(new RuntimeException("handshake lost"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> mcpServerService.connect("s1"));
        assertEquals(5011, e.getCode());
        verify(connectionManager).close("s1");
        verify(skillRegistry, never()).register(any());
    }

    // ---------- 虚拟 Skill ID 工具方法（供 delete 保护使用） ----------

    @Test
    void skillIdPrefix_contractStable() {
        assertEquals("mcp-", McpSkillExecutor.SKILL_ID_PREFIX);
    }
}
