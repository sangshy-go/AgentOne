package com.agentone.skill.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.PageResult;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.dto.BindSkillDTO;
import com.agentone.skill.entity.AgentSkillBindingDO;
import com.agentone.skill.entity.McpToolPublishDO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.McpToolPublishMapper;
import com.agentone.skill.mapper.SkillCallLogMapper;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.vo.AgentSkillBindingVO;
import com.agentone.skill.vo.SkillVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentSkillServiceImpl.listSkills 单测（课题⑧）：
 * 搜索/筛选与虚拟挂载合并逻辑。Mapper 返回固定行（DB 侧条件由集成验证覆盖），
 * 重点验证 Registry 虚拟 Skill 的内存过滤、分类透传与分页 total。
 */
@ExtendWith(MockitoExtension.class)
class AgentSkillServiceImplTest {

    @Mock
    private AgentSkillBindingMapper bindingMapper;
    @Mock
    private SkillMapper skillMapper;
    @Mock
    private SkillRegistry skillRegistry;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private McpToolPublishMapper mcpToolPublishMapper;
    @Mock
    private SkillCallLogMapper callLogMapper;

    @InjectMocks
    private AgentSkillServiceImpl service;

    @BeforeEach
    void setUp() {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    private SkillDO dbRow(String id, String name, String type, String category) {
        SkillDO skill = new SkillDO();
        skill.setId(id);
        skill.setWorkspaceId("ws-1");
        skill.setName(name);
        skill.setType(type);
        skill.setCategory(category);
        skill.setStatus("active");
        skill.setInstalledAt(LocalDateTime.now());
        return skill;
    }

    private SkillDescriptor descriptor(String id, String name, String type,
                                       String category, String workspaceId) {
        return SkillDescriptor.builder()
                .id(id).name(name).type(type).category(category)
                .workspaceId(workspaceId)
                .inputSchema(Map.of())
                .build();
    }

    private void mockDb(List<SkillDO> rows) {
        when(skillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(rows);
    }

    @Test
    void listSkills_noFilter_mergesVirtualsIntoPage1() {
        mockDb(List.of(dbRow("api-1", "征信查询", "api", "风控合规")));
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                descriptor("builtin-http-request", "HTTP 请求", "builtin", "IT集成", null),
                descriptor("mcp-s1-tool", "S1 / tool", "mcp", "IT集成", "ws-1"),
                descriptor("mcp-s2-tool", "S2 / tool", "mcp", "IT集成", "ws-2")));

        PageResult<SkillVO> result = service.listSkills("ws-1", null, null, null, null, 1, 20);

        assertEquals(3, result.getTotal(), "其他空间的 MCP 工具不应计入");
        assertEquals(List.of("builtin-http-request", "mcp-s1-tool", "api-1"),
                result.getRecords().stream().map(SkillVO::getId).toList(),
                "虚拟挂载应置顶，跨空间 MCP 应被排除");
        assertEquals("IT集成", result.getRecords().get(0).getCategory(),
                "虚拟 Skill 的分类必须透传给前端");
    }

    @Test
    void listSkills_keywordFilter_matchesVirtualNameAndDescription() {
        mockDb(List.of());
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                descriptor("builtin-http-request", "HTTP 请求", "builtin", "IT集成", null),
                descriptor("builtin-code-execute", "代码执行", "builtin", "IT集成", null)));

        PageResult<SkillVO> hit = service.listSkills("ws-1", "http", null, null, null, 1, 20);
        assertEquals(1, hit.getTotal());
        assertEquals("builtin-http-request", hit.getRecords().get(0).getId());

        // 描述匹配也算命中
        PageResult<SkillVO> descHit = service.listSkills("ws-1", "代码", null, null, null, 1, 20);
        assertEquals(1, descHit.getTotal());

        PageResult<SkillVO> miss = service.listSkills("ws-1", "不存在的词", null, null, null, 1, 20);
        assertEquals(0, miss.getTotal());
        assertTrue(miss.getRecords().isEmpty());
    }

    @Test
    void listSkills_categoryFilter_filtersVirtualsInMemory() {
        mockDb(List.of());
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                descriptor("builtin-http-request", "HTTP 请求", "builtin", "IT集成", null),
                descriptor("builtin-knowledge-search", "知识库检索", "builtin", "办公效率", null)));

        PageResult<SkillVO> result = service.listSkills("ws-1", null, "办公效率", null, null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("builtin-knowledge-search", result.getRecords().get(0).getId());
    }

    @Test
    void listSkills_typeFilter_excludesOtherVirtualTypes() {
        mockDb(List.of());
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                descriptor("builtin-http-request", "HTTP 请求", "builtin", "IT集成", null),
                descriptor("mcp-s1-tool", "S1 / tool", "mcp", "IT集成", "ws-1")));

        PageResult<SkillVO> result = service.listSkills("ws-1", null, null, "builtin", null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("builtin-http-request", result.getRecords().get(0).getId());
    }

    @Test
    void listSkills_page2_virtualsTrimmedToWindowNoMisalignment() {
        mockDb(List.of(
                dbRow("api-1", "征信查询", "api", "风控合规"),
                dbRow("api-2", "合同审查", "api", "风控合规"),
                dbRow("api-3", "发票核验", "api", "风控合规")));
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                descriptor("builtin-http-request", "HTTP 请求", "builtin", "IT集成", null)));

        // 合并列表 = [builtin, api-1, api-2, api-3]，size=2
        PageResult<SkillVO> p1 = service.listSkills("ws-1", null, null, null, null, 1, 2);
        assertEquals(4, p1.getTotal(), "total 必须包含虚拟条数");
        assertEquals(List.of("builtin-http-request", "api-1"),
                p1.getRecords().stream().map(SkillVO::getId).toList(),
                "虚拟挂载置顶，第一页窗口需裁剪到 size");

        PageResult<SkillVO> p2 = service.listSkills("ws-1", null, null, null, null, 2, 2);
        assertEquals(4, p2.getTotal(), "翻页 total 必须一致");
        assertEquals(List.of("api-2", "api-3"),
                p2.getRecords().stream().map(SkillVO::getId).toList(),
                "第二页应承接合并窗口，虚拟 Skill 不再重复出现，且不再错位");
    }

    @Test
    void listSkills_statusFilter_nonActiveSkipsVirtuals() {
        SkillDO disabledRow = dbRow("api-1", "征信查询", "api", "风控合规");
        disabledRow.setStatus("disabled");
        mockDb(List.of(disabledRow));
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                descriptor("builtin-http-request", "HTTP 请求", "builtin", "IT集成", null)));

        // 管理视角：不传 status 返回全部（含停用），虚拟挂载照常合并
        PageResult<SkillVO> all = service.listSkills("ws-1", null, null, null, null, 1, 20);
        assertEquals(2, all.getTotal(), "不传 status 时停用技能也需返回，供重新启用");

        // 按非 active 状态过滤：虚拟 Skill 恒为 active，不应合并
        PageResult<SkillVO> disabled = service.listSkills("ws-1", null, null, null, "disabled", 1, 20);
        assertEquals(1, disabled.getTotal(), "按非 active 状态过滤时不应合并虚拟挂载");
        assertEquals(List.of("api-1"),
                disabled.getRecords().stream().map(SkillVO::getId).toList());

        // 绑定选择器视角：status=active 时虚拟挂载（恒为 active）仍需合并
        PageResult<SkillVO> active = service.listSkills("ws-1", null, null, null, "active", 1, 20);
        assertEquals(2, active.getTotal(), "status=active 时虚拟挂载仍需合并");
    }

    // ---------- listPlaza（技能广场，Skill 中心 v2） ----------

    private McpToolPublishDO publishRow(String serverId, String toolName) {
        McpToolPublishDO row = new McpToolPublishDO();
        row.setWorkspaceId("ws-1");
        row.setServerId(serverId);
        row.setToolName(toolName);
        row.setPublished(true);
        return row;
    }

    @Test
    void listPlaza_includesUserSkillsBuiltinsAndPublishedMcpOnly() {
        when(skillMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(dbRow("api-1", "征信查询", "api", "法务合规")));
        when(skillRegistry.listDescriptors()).thenReturn(List.of(
                descriptor("builtin-http-request", "HTTP 请求", "builtin", "IT集成", null),
                descriptor("mcp-s1-tool", "S1 / tool", "mcp", "IT集成", "ws-1"),
                descriptor("mcp-s1-unpub", "S1 / unpub", "mcp", "IT集成", "ws-1"),
                descriptor("mcp-s2-tool", "S2 / tool", "mcp", "IT集成", "ws-2")));
        when(mcpToolPublishMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(publishRow("s1", "tool")));

        List<SkillVO> plaza = service.listPlaza("ws-1", null, null);

        assertEquals(List.of("api-1", "builtin-http-request", "mcp-s1-tool"),
                plaza.stream().map(SkillVO::getId).toList(),
                "广场 = 用户 Skill + builtin + 已发布 MCP；未发布与跨空间 MCP 必须排除");
        assertTrue(plaza.get(2).getPublished());
    }

    @Test
    void listPlaza_callCountEnriched() {
        when(skillMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(dbRow("api-1", "征信查询", "api", "法务合规")));
        when(skillRegistry.listDescriptors()).thenReturn(List.of());
        when(callLogMapper.countCallsGroupBySkill()).thenReturn(
                List.of(Map.of("skillId", "api-1", "callCount", 7L)));

        List<SkillVO> plaza = service.listPlaza("ws-1", null, null);

        assertEquals(7L, plaza.get(0).getCallCount(), "广场卡片需展示真实调用量");
    }

    // ---------- bind 的 MCP 发布门禁 ----------

    private void mockVirtualMcpExecutor() {
        SkillExecutor executor = mock(SkillExecutor.class);
        when(executor.getDescriptor()).thenReturn(
                descriptor("mcp-s1-tool", "S1 / tool", "mcp", "IT集成", "ws-1"));
        when(skillRegistry.getExecutor("mcp-s1-tool")).thenReturn(Optional.of(executor));
    }

    @Test
    void bind_unpublishedMcpTool_throws5016() {
        BindSkillDTO dto = new BindSkillDTO();
        dto.setAgentId("agent-1");
        dto.setSkillId("mcp-s1-tool");
        when(bindingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(skillMapper.selectById("mcp-s1-tool")).thenReturn(null);
        mockVirtualMcpExecutor();
        when(mcpToolPublishMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.bind(dto));
        assertEquals(5016, e.getCode(), "未发布的 MCP 工具不允许绑定启用");
        verify(bindingMapper, never()).insert(any(AgentSkillBindingDO.class));
    }

    @Test
    void bind_publishedMcpTool_success() {
        BindSkillDTO dto = new BindSkillDTO();
        dto.setAgentId("agent-1");
        dto.setSkillId("mcp-s1-tool");
        when(bindingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(skillMapper.selectById("mcp-s1-tool")).thenReturn(null);
        mockVirtualMcpExecutor();
        when(mcpToolPublishMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(publishRow("s1", "tool")));

        AgentSkillBindingVO vo = service.bind(dto);

        assertEquals("mcp-s1-tool", vo.getSkillId());
        verify(bindingMapper).insert(any(AgentSkillBindingDO.class));
    }
}
