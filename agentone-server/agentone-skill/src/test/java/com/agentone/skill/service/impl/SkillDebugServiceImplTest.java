package com.agentone.skill.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.skill.core.SkillCallLogRecorder;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.dto.DebugPreviewDTO;
import com.agentone.skill.dto.DebugRunDTO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.vo.DebugPreviewVO;
import com.agentone.skill.vo.DebugRunVO;
import com.agentone.skill.vo.DebugTargetVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SkillDebugServiceImpl 单测（课题③）：
 * 调试目标租户过滤、参数预检、真实执行的审计与越权防护。
 */
@ExtendWith(MockitoExtension.class)
class SkillDebugServiceImplTest {

    @Mock
    private SkillRegistry skillRegistry;
    @Mock
    private SkillMapper skillMapper;
    @Mock
    private SkillCallLogRecorder callLogRecorder;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SkillDebugServiceImpl skillDebugService;

    @BeforeEach
    void setUp() {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    private SkillDescriptor descriptor(String id, String type, String wsId) {
        return SkillDescriptor.builder()
                .id(id)
                .name(id)
                .type(type)
                .source("src-" + id)
                .workspaceId(wsId)
                .inputSchema(Map.of("type", "object", "properties", Map.of()))
                .build();
    }

    private SkillExecutor executorOf(SkillDescriptor descriptor) {
        SkillExecutor executor = mock(SkillExecutor.class);
        when(executor.getDescriptor()).thenReturn(descriptor);
        return executor;
    }

    // ---------- listTargets ----------

    @Test
    void listTargets_filtersOtherWorkspaceApiAndMcp() {
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                descriptor("builtin-a", "builtin", null),
                descriptor("api-own", "api", "ws-1"),
                descriptor("api-other", "api", "ws-2"),
                descriptor("mcp-own", "mcp", "ws-1"),
                descriptor("mcp-other", "mcp", "ws-2")));

        List<DebugTargetVO> targets = skillDebugService.listTargets();

        List<String> ids = targets.stream().map(DebugTargetVO::getId).toList();
        assertEquals(List.of("api-own", "builtin-a", "mcp-own"), ids,
                "其他工作空间的 api/mcp 均不可见，按 type+name 排序");
    }

    // ---------- preview ----------

    @Test
    void preview_missingRequired_returnsErrors() {
        SkillDescriptor d = SkillDescriptor.builder()
                .id("builtin-weather").name("天气查询").type("builtin")
                .inputSchema(Map.of(
                        "type", "object",
                        "required", List.of("city"),
                        "properties", Map.of("city", Map.of("type", "string"))))
                .build();
        SkillExecutor executor = executorOf(d);
        when(skillRegistry.getExecutor("builtin-weather")).thenReturn(Optional.of(executor));

        DebugPreviewDTO dto = new DebugPreviewDTO();
        dto.setSkillId("builtin-weather");
        dto.setParams(Map.of());
        DebugPreviewVO vo = skillDebugService.preview(dto);

        assertFalse(vo.isValid());
        assertTrue(vo.getErrors().get(0).contains("city"));
        assertTrue(vo.getPlan().contains("内置执行器直接执行"));
    }

    @Test
    void preview_validApiSkill_planShowsHttpTarget() {
        SkillDescriptor d = descriptor("skill-1", "api", "ws-1");
        SkillExecutor executor = executorOf(d);
        when(skillRegistry.getExecutor("skill-1")).thenReturn(Optional.of(executor));
        SkillDO skill = new SkillDO();
        skill.setId("skill-1");
        skill.setConfig("{\"url\":\"http://9.9.9.9/weather\",\"method\":\"get\"}");
        when(skillMapper.selectById("skill-1")).thenReturn(skill);

        DebugPreviewDTO dto = new DebugPreviewDTO();
        dto.setSkillId("skill-1");
        dto.setParams(Map.of("city", "北京"));
        DebugPreviewVO vo = skillDebugService.preview(dto);

        assertTrue(vo.isValid());
        assertTrue(vo.getErrors().isEmpty());
        assertTrue(vo.getPlan().startsWith("HTTP GET http://9.9.9.9/weather"),
                "执行计划应展示方法与目标地址，实际: " + vo.getPlan());
    }

    @Test
    void preview_mcpSkill_planMentionsSource() {
        SkillDescriptor d = SkillDescriptor.builder()
                .id("mcp-s1-tool").name("工具").type("mcp").source("天气服务")
                .workspaceId("ws-1").inputSchema(Map.of()).build();
        SkillExecutor executor = executorOf(d);
        when(skillRegistry.getExecutor("mcp-s1-tool")).thenReturn(Optional.of(executor));

        DebugPreviewDTO dto = new DebugPreviewDTO();
        dto.setSkillId("mcp-s1-tool");
        dto.setParams(Map.of());
        DebugPreviewVO vo = skillDebugService.preview(dto);

        assertTrue(vo.getPlan().contains("天气服务"));
    }

    @Test
    void preview_unknownSkill_throws5002() {
        when(skillRegistry.getExecutor("no-such")).thenReturn(Optional.empty());
        DebugPreviewDTO dto = new DebugPreviewDTO();
        dto.setSkillId("no-such");
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillDebugService.preview(dto));
        assertEquals(5002, e.getCode());
    }

    @Test
    void preview_crossTenantSkill_throws5004() {
        SkillDescriptor d = descriptor("skill-other", "api", "ws-2");
        SkillExecutor executor = executorOf(d);
        when(skillRegistry.getExecutor("skill-other")).thenReturn(Optional.of(executor));

        DebugPreviewDTO dto = new DebugPreviewDTO();
        dto.setSkillId("skill-other");
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillDebugService.preview(dto));
        assertEquals(5004, e.getCode(), "Registry 跨租户共享，必须按描述符归属拦截");
    }

    // ---------- run ----------

    private DebugRunDTO runDto(String skillId, Map<String, Object> params) {
        DebugRunDTO dto = new DebugRunDTO();
        dto.setSkillId(skillId);
        dto.setParams(params);
        return dto;
    }

    @Test
    void run_success_executesAndRecordsAudit() {
        SkillDescriptor d = descriptor("builtin-echo", "builtin", null);
        SkillExecutor executor = executorOf(d);
        when(executor.execute(any(), any())).thenReturn(SkillResult.success(Map.of("echo", "hi"), 12L));
        when(skillRegistry.getExecutor("builtin-echo")).thenReturn(Optional.of(executor));

        DebugRunVO vo = skillDebugService.run(runDto("builtin-echo", Map.of("msg", "hi")));

        assertTrue(vo.isSuccess());
        assertEquals("hi", vo.getData().get("echo"));
        assertEquals(12L, vo.getDurationMs());
        assertTrue(vo.getTraceId().startsWith("debug-"));
        assertEquals(vo.getTraceId(), vo.getSessionId(), "未传 sessionId 时应回落到 traceId");

        verify(callLogRecorder).record(eq("ws-1"), eq("debugger"), eq(vo.getSessionId()),
                eq("builtin-echo"), eq(vo.getTraceId()), anyString(), anyString(),
                eq(12L), eq(true), isNull());
    }

    @Test
    void run_customSessionId_used() {
        SkillDescriptor d = descriptor("builtin-echo", "builtin", null);
        SkillExecutor executor = executorOf(d);
        when(executor.execute(any(), any())).thenReturn(SkillResult.success(Map.of(), 1L));
        when(skillRegistry.getExecutor("builtin-echo")).thenReturn(Optional.of(executor));

        DebugRunDTO dto = runDto("builtin-echo", Map.of());
        dto.setSessionId("sess-9");
        DebugRunVO vo = skillDebugService.run(dto);

        assertEquals("sess-9", vo.getSessionId());
    }

    @Test
    void run_crossTenantSkill_throws5004WithoutExecution() {
        SkillDescriptor d = descriptor("mcp-other-tool", "mcp", "ws-2");
        SkillExecutor executor = executorOf(d);
        when(skillRegistry.getExecutor("mcp-other-tool")).thenReturn(Optional.of(executor));

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillDebugService.run(runDto("mcp-other-tool", Map.of())));
        assertEquals(5004, e.getCode());
        verify(executor, never()).execute(any(), any());
        verify(callLogRecorder, never()).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void run_executorThrows_returnsFailureAndAudits() {
        SkillDescriptor d = descriptor("builtin-boom", "builtin", null);
        SkillExecutor executor = executorOf(d);
        when(executor.execute(any(), any())).thenThrow(new RuntimeException("boom"));
        when(skillRegistry.getExecutor("builtin-boom")).thenReturn(Optional.of(executor));

        DebugRunVO vo = skillDebugService.run(runDto("builtin-boom", Map.of()));

        assertFalse(vo.isSuccess());
        assertTrue(vo.getErrorMessage().contains("boom"));
        verify(callLogRecorder).record(eq("ws-1"), eq("debugger"), anyString(),
                eq("builtin-boom"), anyString(), anyString(), anyString(), anyLong(),
                eq(false), anyString());
    }

    @Test
    void run_unknownSkill_throws5002() {
        when(skillRegistry.getExecutor("no-such")).thenReturn(Optional.empty());
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillDebugService.run(runDto("no-such", Map.of())));
        assertEquals(5002, e.getCode());
    }
}
