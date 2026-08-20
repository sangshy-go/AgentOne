package com.agentone.skill.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.skill.core.ConfirmTokenStore;
import com.agentone.skill.core.SkillCallLogRecorder;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.dto.SkillDTO;
import com.agentone.skill.dto.SkillImportDTO;
import com.agentone.skill.entity.McpServerDO;
import com.agentone.skill.entity.McpToolPublishDO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.entity.SkillPackageFileDO;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.McpServerMapper;
import com.agentone.skill.mapper.McpToolPublishMapper;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.mapper.SkillPackageFileMapper;
import com.agentone.skill.vo.InvokeSkillVO;
import com.agentone.skill.vo.SkillExportVO;
import com.agentone.skill.vo.SkillPackageFileVO;
import com.agentone.skill.vo.SkillVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SkillServiceImpl 单测：API 模式 Skill 的 CRUD 与动态注册逻辑。
 * Mapper / Registry / WebClient 全部 mock，不依赖 DB 与网络。
 */
@ExtendWith(MockitoExtension.class)
class SkillServiceImplTest {

    @Mock
    private SkillMapper skillMapper;
    @Mock
    private AgentSkillBindingMapper bindingMapper;
    @Mock
    private SkillRegistry skillRegistry;
    @Mock
    private WebClient webClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private SkillPackageFileMapper packageFileMapper;
    @Mock
    private McpToolPublishMapper mcpToolPublishMapper;
    @Mock
    private McpServerMapper mcpServerMapper;
    @Mock
    private ConfirmTokenStore confirmTokenStore;
    @Mock
    private SkillCallLogRecorder callLogRecorder;

    @InjectMocks
    private SkillServiceImpl skillService;

    @BeforeEach
    void setUp() {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    private SkillDTO validDto() {
        SkillDTO dto = new SkillDTO();
        dto.setName("天气查询");
        dto.setDescription("查询城市天气");
        dto.setInputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");
        dto.setConfig("{\"url\":\"https://9.9.9.9/weather\",\"method\":\"GET\"}");
        return dto;
    }

    @Test
    void create_success_insertsAndRegisters() {
        when(skillMapper.insert(any(SkillDO.class))).thenAnswer(inv -> {
            SkillDO s = inv.getArgument(0);
            s.setId("generated-id");
            return 1;
        });

        SkillVO vo = skillService.create(validDto());

        assertEquals("generated-id", vo.getId());
        assertEquals("api", vo.getType());
        assertEquals("custom", vo.getSource());
        assertEquals("active", vo.getStatus());
        assertEquals("1.0.0", vo.getVersion(), "未指定版本时应默认 1.0.0");
        assertEquals("ws-1", vo.getWorkspaceId());

        ArgumentCaptor<SkillExecutor> captor = ArgumentCaptor.forClass(SkillExecutor.class);
        verify(skillRegistry).register(captor.capture());
        assertEquals("generated-id", captor.getValue().getDescriptor().getId(),
                "注册进 Registry 的执行器必须对应新建的 Skill");
    }

    @Test
    void create_invalidConfigJson_throws() {
        SkillDTO dto = validDto();
        dto.setConfig("不是 JSON");
        BusinessException e = assertThrows(BusinessException.class, () -> skillService.create(dto));
        assertEquals(5007, e.getCode());
        verify(skillMapper, never()).insert(any(SkillDO.class));
    }

    @Test
    void create_configMissingUrl_throws() {
        SkillDTO dto = validDto();
        dto.setConfig("{\"method\":\"GET\"}");
        BusinessException e = assertThrows(BusinessException.class, () -> skillService.create(dto));
        assertEquals(5007, e.getCode());
    }

    @Test
    void create_ssrfUrl_throws() {
        SkillDTO dto = validDto();
        dto.setConfig("{\"url\":\"http://192.168.1.1/internal\"}");
        BusinessException e = assertThrows(BusinessException.class, () -> skillService.create(dto));
        assertEquals(5008, e.getCode());
        verify(skillRegistry, never()).register(any());
    }

    @Test
    void create_invalidInputSchema_throws() {
        SkillDTO dto = validDto();
        dto.setInputSchema("坏掉的 schema");
        BusinessException e = assertThrows(BusinessException.class, () -> skillService.create(dto));
        assertEquals(5007, e.getCode());
    }

    @Test
    void update_notFound_throws() {
        when(skillMapper.selectById("missing")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.update("missing", validDto()));
        assertEquals(5002, e.getCode());
    }

    @Test
    void update_builtinSkill_rejected() {
        SkillDO builtin = new SkillDO();
        builtin.setId("builtin-http-request");
        builtin.setType("builtin");
        when(skillMapper.selectById("builtin-http-request")).thenReturn(builtin);

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.update("builtin-http-request", validDto()));
        assertEquals(5006, e.getCode(), "内置 Skill 不允许通过 Skill 中心修改");
    }

    @Test
    void update_success_refreshesRegistry() {
        SkillDO existing = new SkillDO();
        existing.setId("skill-1");
        existing.setType("api");
        existing.setVersion("1.0.0");
        when(skillMapper.selectById("skill-1")).thenReturn(existing);

        SkillDTO dto = validDto();
        dto.setName("改名后的 Skill");
        SkillVO vo = skillService.update("skill-1", dto);

        assertEquals("改名后的 Skill", vo.getName());
        verify(skillMapper).updateById(existing);
        verify(skillRegistry).register(any(SkillExecutor.class));
    }

    @Test
    void delete_withBindings_rejected() {
        SkillDO existing = new SkillDO();
        existing.setId("skill-1");
        existing.setType("api");
        when(skillMapper.selectById("skill-1")).thenReturn(existing);
        when(bindingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class, () -> skillService.delete("skill-1"));
        assertEquals(5005, e.getCode(), "仍有 Agent 绑定时必须拒绝删除");
        verify(skillMapper, never()).deleteById(anyString());
        verify(skillRegistry, never()).unregister(any());
    }

    @Test
    void delete_success_deletesAndUnregisters() {
        SkillDO existing = new SkillDO();
        existing.setId("skill-1");
        existing.setType("api");
        when(skillMapper.selectById("skill-1")).thenReturn(existing);
        when(bindingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        skillService.delete("skill-1");

        verify(skillRegistry).unregister("skill-1");
        // 顺序锁定：必须先删子表 skill_package_file 再删 skill，否则 FK 冲突（500）
        InOrder order = inOrder(packageFileMapper, skillMapper);
        order.verify(packageFileMapper).delete(any(LambdaQueryWrapper.class));
        order.verify(skillMapper).deleteById("skill-1");
    }

    @Test
    void test_unknownSkill_throws() {
        when(skillRegistry.getExecutor("no-such")).thenReturn(Optional.empty());
        when(skillMapper.selectById("no-such")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.test("no-such", Map.of()));
        assertEquals(5002, e.getCode());
    }

    @Test
    void test_executorInRegistry_executesDirectly() {
        SkillExecutor executor = org.mockito.Mockito.mock(SkillExecutor.class);
        when(executor.execute(any(), any())).thenReturn(SkillResult.success(Map.of("ok", true), 1L));
        // 描述符未标注归属空间（builtin 语义，全局可见）→ 允许直接测试
        when(executor.getDescriptor()).thenReturn(
                SkillDescriptor.builder().id("skill-1").type("builtin").enabled(true).build());
        when(skillRegistry.getExecutor("skill-1")).thenReturn(Optional.of(executor));

        SkillResult result = skillService.test("skill-1", Map.of("city", "北京"));

        assertTrue(result.isSuccess());
        assertEquals(true, result.getData().get("ok"));
        verify(skillMapper, never()).selectById(any());
    }

    @Test
    void test_disabledExecutorInRegistry_rejectedWith5016() {
        // 已停用的技能（描述符 enabled=false）即使位于 Registry，也不应被调用
        SkillExecutor executor = org.mockito.Mockito.mock(SkillExecutor.class);
        when(executor.getDescriptor()).thenReturn(
                SkillDescriptor.builder().id("skill-off").type("builtin").enabled(false).build());
        when(skillRegistry.getExecutor("skill-off")).thenReturn(Optional.of(executor));

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.test("skill-off", Map.of()));
        assertEquals(5016, e.getCode(), "已停用技能必须被拦截，禁止执行");
        verify(executor, never()).execute(any(), any());
    }

    @Test
    void test_crossTenantExecutor_throws5004() {
        SkillExecutor executor = org.mockito.Mockito.mock(SkillExecutor.class);
        when(executor.getDescriptor()).thenReturn(
                SkillDescriptor.builder().id("skill-x").type("api").workspaceId("ws-2").enabled(true).build());
        when(skillRegistry.getExecutor("skill-x")).thenReturn(Optional.of(executor));

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.test("skill-x", Map.of()));
        assertEquals(5004, e.getCode(), "Registry 跨租户共享，必须按描述符归属拦截");
        verify(executor, never()).execute(any(), any());
    }

    @Test
    void test_registryMissButDbHit_buildsExecutorOffline() {
        // Registry 无执行器时按 DB 行临时构建执行器（技能处于启用状态）。
        // url 使用无法解析的主机名：UrlSafetyUtil 直接拒绝，不会发起真实请求。
        SkillDO skill = new SkillDO();
        skill.setId("skill-1");
        skill.setType("api");
        skill.setStatus("active");
        skill.setConfig("{\"url\":\"http://agentone-test-unresolvable.invalid/api\"}");
        when(skillRegistry.getExecutor("skill-1")).thenReturn(Optional.empty());
        when(skillMapper.selectById("skill-1")).thenReturn(skill);

        SkillResult result = skillService.test("skill-1", Map.of());

        assertFalse(result.isSuccess(), "无法解析的主机应返回失败而不是抛异常");
    }

    @Test
    void test_nonApiSkillNotInRegistry_rejected() {
        SkillDO builtin = new SkillDO();
        builtin.setId("builtin-x");
        builtin.setType("builtin");
        when(skillRegistry.getExecutor("builtin-x")).thenReturn(Optional.empty());
        when(skillMapper.selectById("builtin-x")).thenReturn(builtin);

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.test("builtin-x", Map.of()));
        assertEquals(5006, e.getCode());
    }

    // ---------- prompt（内容型）Skill ----------

    private SkillDTO validPromptDto() {
        SkillDTO dto = new SkillDTO();
        dto.setName("信贷审批报告规范");
        dto.setType("prompt");
        dto.setDescription("撰写信贷审批报告时加载的格式与合规要求");
        dto.setConfig("{\"content\":\"## 规范\\n1. 先给结论\"}");
        return dto;
    }

    @Test
    void create_promptType_insertsAndRegisters() {
        when(skillMapper.insert(any(SkillDO.class))).thenAnswer(inv -> {
            SkillDO s = inv.getArgument(0);
            s.setId("prompt-id");
            return 1;
        });

        SkillVO vo = skillService.create(validPromptDto());

        assertEquals("prompt", vo.getType());
        assertEquals("custom", vo.getSource());
        assertEquals("{}", vo.getInputSchema(), "内容型 Skill 无参数，schema 应强制为空");
        assertEquals("{}", vo.getOutputSchema());

        ArgumentCaptor<SkillExecutor> captor = ArgumentCaptor.forClass(SkillExecutor.class);
        verify(skillRegistry).register(captor.capture());
        assertEquals("prompt", captor.getValue().getDescriptor().getType(),
                "注册进 Registry 的必须是 PromptSkillExecutor");
    }

    @Test
    void create_promptMissingContent_throws5007() {
        SkillDTO dto = validPromptDto();
        dto.setConfig("{}");
        BusinessException e = assertThrows(BusinessException.class, () -> skillService.create(dto));
        assertEquals(5007, e.getCode());
        verify(skillMapper, never()).insert(any(SkillDO.class));
    }

    @Test
    void create_unknownType_throws5007() {
        SkillDTO dto = validPromptDto();
        dto.setType("workflow");
        BusinessException e = assertThrows(BusinessException.class, () -> skillService.create(dto));
        assertEquals(5007, e.getCode());
    }

    @Test
    void update_typeChange_rejected() {
        SkillDO existing = new SkillDO();
        existing.setId("skill-1");
        existing.setType("api");
        existing.setVersion("1.0.0");
        when(skillMapper.selectById("skill-1")).thenReturn(existing);

        SkillDTO dto = validPromptDto();
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.update("skill-1", dto));
        assertEquals(5007, e.getCode(), "api → prompt 类型变更必须拒绝");
        verify(skillMapper, never()).updateById(any(SkillDO.class));
    }

    @Test
    void update_prompt_success_refreshesRegistry() {
        SkillDO existing = new SkillDO();
        existing.setId("prompt-1");
        existing.setType("prompt");
        existing.setInputSchema("{}");
        existing.setOutputSchema("{}");
        existing.setVersion("1.0.0");
        when(skillMapper.selectById("prompt-1")).thenReturn(existing);

        SkillDTO dto = validPromptDto();
        dto.setConfig("{\"content\":\"改过的内容\"}");
        SkillVO vo = skillService.update("prompt-1", dto);

        assertEquals("{\"content\":\"改过的内容\"}", vo.getConfig());
        verify(skillMapper).updateById(existing);
        verify(skillRegistry).register(any(SkillExecutor.class));
    }

    @Test
    void test_promptDbFallback_returnsContent() {
        // Registry 缺失时按 DB 行临时构建 PromptSkillExecutor（技能处于启用状态）
        SkillDO skill = new SkillDO();
        skill.setId("prompt-1");
        skill.setType("prompt");
        skill.setName("报告规范");
        skill.setStatus("active");
        skill.setConfig("{\"content\":\"指令全文\"}");
        when(skillRegistry.getExecutor("prompt-1")).thenReturn(Optional.empty());
        when(skillMapper.selectById("prompt-1")).thenReturn(skill);

        SkillResult result = skillService.test("prompt-1", Map.of());

        assertTrue(result.isSuccess());
        assertEquals("指令全文", result.getData().get("content"));
    }

    // ---------- 导出 / 导入 ----------

    private SkillDO promptSkillRow() {
        SkillDO skill = new SkillDO();
        skill.setId("prompt-1");
        skill.setWorkspaceId("ws-1");
        skill.setName("信贷审批报告规范");
        skill.setType("prompt");
        skill.setSource("custom");
        skill.setCategory("风控合规");
        skill.setDescription("描述");
        skill.setInputSchema("{}");
        skill.setOutputSchema("{}");
        skill.setConfig("{\"content\":\"指令全文\"}");
        skill.setVersion("1.2.0");
        skill.setStatus("active");
        return skill;
    }

    @Test
    void export_returnsFullDefinition() {
        when(skillMapper.selectById("prompt-1")).thenReturn(promptSkillRow());

        SkillExportVO vo = skillService.export("prompt-1");

        assertEquals("agentone-skill", vo.getFormat());
        assertEquals(1, vo.getFormatVersion());
        assertEquals("信贷审批报告规范", vo.getName());
        assertEquals("prompt", vo.getType());
        assertEquals("风控合规", vo.getCategory(), "导出定义需携带业务分类");
        assertEquals("{\"content\":\"指令全文\"}", vo.getConfig());
        assertEquals("1.2.0", vo.getVersion());
        assertNotNull(vo.getExportedAt());
    }

    @Test
    void export_builtinSkill_rejected() {
        SkillDO builtin = new SkillDO();
        builtin.setId("builtin-x");
        builtin.setType("builtin");
        when(skillMapper.selectById("builtin-x")).thenReturn(builtin);

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.export("builtin-x"));
        assertEquals(5006, e.getCode(), "虚拟挂载 Skill 无 DB 行，不可导出");
    }

    @Test
    void import_success_sourceMarkedImported() {
        when(skillMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(skillMapper.insert(any(SkillDO.class))).thenAnswer(inv -> {
            SkillDO s = inv.getArgument(0);
            s.setId("imported-id");
            return 1;
        });

        SkillImportDTO dto = new SkillImportDTO();
        dto.setFormat("agentone-skill");
        dto.setName("信贷审批报告规范");
        dto.setType("prompt");
        dto.setConfig("{\"content\":\"指令全文\"}");
        dto.setVersion("1.2.0");

        SkillVO vo = skillService.importSkill(dto);

        assertEquals("imported", vo.getSource(), "导入的 Skill 必须可区分来源");
        assertEquals("prompt", vo.getType());
        verify(skillRegistry).register(any(SkillExecutor.class));
    }

    @Test
    void import_duplicateName_throws5013() {
        when(skillMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        SkillImportDTO dto = new SkillImportDTO();
        dto.setName("信贷审批报告规范");
        dto.setType("prompt");
        dto.setConfig("{\"content\":\"x\"}");

        BusinessException e = assertThrows(BusinessException.class, () -> skillService.importSkill(dto));
        assertEquals(5013, e.getCode());
        verify(skillMapper, never()).insert(any(SkillDO.class));
    }

    @Test
    void import_foreignFormat_throws5007() {
        SkillImportDTO dto = new SkillImportDTO();
        dto.setFormat("other-platform");
        dto.setName("X");
        dto.setConfig("{\"content\":\"x\"}");

        BusinessException e = assertThrows(BusinessException.class, () -> skillService.importSkill(dto));
        assertEquals(5007, e.getCode());
    }

    @Test
    void import_promptMissingContent_throws5007() {
        when(skillMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        SkillImportDTO dto = new SkillImportDTO();
        dto.setName("X");
        dto.setType("prompt");
        dto.setConfig("{}");

        BusinessException e = assertThrows(BusinessException.class, () -> skillService.importSkill(dto));
        assertEquals(5007, e.getCode(), "导入必须走与创建相同的校验链路");
        verify(skillMapper, never()).insert(any(SkillDO.class));
    }

    // ---------- 业务分类（课题⑧） ----------

    @Test
    void create_withCategory_persisted() {
        when(skillMapper.insert(any(SkillDO.class))).thenAnswer(inv -> {
            SkillDO s = inv.getArgument(0);
            s.setId("cat-id");
            return 1;
        });

        SkillDTO dto = validPromptDto();
        dto.setCategory("风控合规");
        SkillVO vo = skillService.create(dto);

        assertEquals("风控合规", vo.getCategory());
    }

    @Test
    void create_blankCategory_defaultsToOther() {
        when(skillMapper.insert(any(SkillDO.class))).thenAnswer(inv -> {
            SkillDO s = inv.getArgument(0);
            s.setId("cat-id");
            return 1;
        });

        SkillVO vo = skillService.create(validDto());

        assertEquals("其他", vo.getCategory(), "未指定分类应归入'其他'");
    }

    @Test
    void update_changesCategory() {
        SkillDO existing = new SkillDO();
        existing.setId("skill-1");
        existing.setType("api");
        existing.setVersion("1.0.0");
        existing.setCategory("其他");
        when(skillMapper.selectById("skill-1")).thenReturn(existing);

        SkillDTO dto = validDto();
        dto.setCategory("财务");
        SkillVO vo = skillService.update("skill-1", dto);

        assertEquals("财务", vo.getCategory());
    }

    @Test
    void import_withCategory_persisted() {
        when(skillMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(skillMapper.insert(any(SkillDO.class))).thenAnswer(inv -> {
            SkillDO s = inv.getArgument(0);
            s.setId("imported-id");
            return 1;
        });

        SkillImportDTO dto = new SkillImportDTO();
        dto.setName("入职流程问答");
        dto.setType("prompt");
        dto.setConfig("{\"content\":\"x\"}");
        dto.setCategory("人力资源");

        SkillVO vo = skillService.importSkill(dto);

        assertEquals("人力资源", vo.getCategory(), "导入需保留导出文件中的分类");
    }

    // ---------- importPackage（技能包导入，Skill 中心 v2） ----------

    private MockMultipartFile packageFile(String path, String content) {
        return new MockMultipartFile("files", path, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private String skillMd(String name) {
        return "---\nname: " + name + "\ndescription: 自动汇总周报\ncategory: 数据分析\n---\n## 步骤\n1. 收集数据";
    }

    @Test
    void importPackage_folderWithSkillMd_parsesFrontmatterAndSavesFiles() {
        when(skillMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(skillMapper.insert(any(SkillDO.class))).thenAnswer(inv -> {
            SkillDO s = inv.getArgument(0);
            s.setId("pkg-id");
            return 1;
        });

        List<MultipartFile> files = List.of(
                packageFile("SKILL.md", skillMd("周报生成")),
                packageFile("scripts/gen.py", "print('hi')"),
                packageFile("resources/template.docx", "doc"));
        List<String> paths = List.of("SKILL.md", "scripts/gen.py", "resources/template.docx");

        SkillVO vo = skillService.importPackage(null, files, paths);

        assertEquals("周报生成", vo.getName());
        assertEquals("prompt", vo.getType(), "技能包统一按内容型 Skill 存储");
        assertEquals("imported", vo.getSource());
        assertEquals("数据分析", vo.getCategory(), "frontmatter category 应透传");

        ArgumentCaptor<SkillDO> skillCaptor = ArgumentCaptor.forClass(SkillDO.class);
        verify(skillMapper).insert(skillCaptor.capture());
        String config = skillCaptor.getValue().getConfig();
        assertTrue(config.contains("## 步骤"), "正文必须去掉 frontmatter 后存储");
        assertFalse(config.contains("name:"), "frontmatter 不应进入正文");

        ArgumentCaptor<SkillPackageFileDO> fileCaptor = ArgumentCaptor.forClass(SkillPackageFileDO.class);
        verify(packageFileMapper, times(3)).insert(fileCaptor.capture());
        Map<String, String> kinds = fileCaptor.getAllValues().stream()
                .collect(Collectors.toMap(SkillPackageFileDO::getPath, SkillPackageFileDO::getKind));
        assertEquals("doc", kinds.get("SKILL.md"));
        assertEquals("script", kinds.get("scripts/gen.py"), "scripts/ 目录必须判为脚本");
        assertEquals("resource", kinds.get("resources/template.docx"));
    }

    @Test
    void importPackage_zipUpload_stripsCommonRoot() throws Exception {
        when(skillMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(skillMapper.insert(any(SkillDO.class))).thenAnswer(inv -> {
            SkillDO s = inv.getArgument(0);
            s.setId("zip-id");
            return 1;
        });

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("weekly-report/SKILL.md"));
            zos.write(skillMd("周报生成").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("weekly-report/scripts/run.py"));
            zos.write("print(1)".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MockMultipartFile zip = new MockMultipartFile("file", "pkg.zip",
                "application/zip", bos.toByteArray());

        SkillVO vo = skillService.importPackage(zip, null, null);

        assertEquals("周报生成", vo.getName());
        ArgumentCaptor<SkillPackageFileDO> captor = ArgumentCaptor.forClass(SkillPackageFileDO.class);
        verify(packageFileMapper, times(2)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream()
                        .allMatch(f -> !f.getPath().startsWith("weekly-report/")),
                "整包上传应剥离公共根目录，保证 SKILL.md 在包根");
    }

    @Test
    void importPackage_missingSkillMd_throws5014() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.importPackage(null,
                        List.of(packageFile("README.md", "# x")), List.of("README.md")));
        assertEquals(5014, e.getCode());
        verify(skillMapper, never()).insert(any(SkillDO.class));
    }

    @Test
    void importPackage_missingFrontmatterName_throws5014() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.importPackage(null,
                        List.of(packageFile("SKILL.md", "---\ndescription: 没有名字\n---\n正文")),
                        List.of("SKILL.md")));
        assertEquals(5014, e.getCode());
    }

    @Test
    void importPackage_zipSlipPath_throws5014() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.importPackage(null,
                        List.of(packageFile("SKILL.md", skillMd("X")),
                                packageFile("evil.sh", "rm -rf /")),
                        List.of("SKILL.md", "../evil.sh")));
        assertEquals(5014, e.getCode(), "含 .. 的路径必须拒绝（zip slip 防护）");
        verify(skillMapper, never()).insert(any(SkillDO.class));
    }

    @Test
    void importPackage_duplicateName_throws5013() {
        when(skillMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.importPackage(null,
                        List.of(packageFile("SKILL.md", skillMd("已存在"))), List.of("SKILL.md")));
        assertEquals(5013, e.getCode());
        verify(skillMapper, never()).insert(any(SkillDO.class));
    }

    @Test
    void importPackage_nothingProvided_throws5014() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.importPackage(null, null, null));
        assertEquals(5014, e.getCode());
    }

    // ---------- listPackageFiles ----------

    @Test
    void listPackageFiles_skillMissing_throws5002() {
        when(skillMapper.selectById("missing")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.listPackageFiles("missing"));
        assertEquals(5002, e.getCode());
    }

    @Test
    void listPackageFiles_mapsRowsToVO() {
        SkillDO skill = new SkillDO();
        skill.setId("pkg-1");
        skill.setType("prompt");
        when(skillMapper.selectById("pkg-1")).thenReturn(skill);
        SkillPackageFileDO row = new SkillPackageFileDO();
        row.setSkillId("pkg-1");
        row.setPath("SKILL.md");
        row.setKind("doc");
        row.setSize(10L);
        row.setContent("正文");
        when(packageFileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row));

        List<SkillPackageFileVO> vos = skillService.listPackageFiles("pkg-1");

        assertEquals(1, vos.size());
        assertEquals("SKILL.md", vos.get(0).getPath());
        assertEquals("doc", vos.get(0).getKind());
        assertEquals("正文", vos.get(0).getContent());
    }

    // ---------- setStatus（启停 toggle） ----------

    @Test
    void setStatus_disableUserSkill_unregistersExecutor() {
        SkillDO skill = new SkillDO();
        skill.setId("skill-1");
        skill.setType("api");
        skill.setStatus("active");
        skill.setConfig("{\"url\":\"https://9.9.9.9/x\"}");
        when(skillMapper.selectById("skill-1")).thenReturn(skill);

        SkillVO vo = skillService.setStatus("skill-1", false);

        assertEquals("disabled", vo.getStatus());
        verify(skillMapper).updateById(skill);
        verify(skillRegistry).unregister("skill-1");
    }

    @Test
    void setStatus_enableUserSkill_registersExecutor() {
        SkillDO skill = new SkillDO();
        skill.setId("skill-1");
        skill.setType("prompt");
        skill.setStatus("disabled");
        skill.setConfig("{\"content\":\"x\"}");
        when(skillMapper.selectById("skill-1")).thenReturn(skill);

        SkillVO vo = skillService.setStatus("skill-1", true);

        assertEquals("active", vo.getStatus());
        verify(skillRegistry).register(any(SkillExecutor.class));
    }

    @Test
    void setStatus_builtinVirtual_throws5006() {
        SkillExecutor executor = mock(SkillExecutor.class);
        when(executor.getDescriptor()).thenReturn(
                SkillDescriptor.builder().id("builtin-x").type("builtin").build());
        when(skillRegistry.getExecutor("builtin-x")).thenReturn(Optional.of(executor));
        when(skillMapper.selectById("builtin-x")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.setStatus("builtin-x", false));
        assertEquals(5006, e.getCode(), "内置技能由系统托管，不允许启停");
    }

    @Test
    void setStatus_mcpToolUpsertsPublishRecord() {
        McpServerDO server = new McpServerDO();
        server.setId("s1");
        server.setWorkspaceId("ws-1");
        when(mcpServerMapper.selectById("s1")).thenReturn(server);
        when(mcpToolPublishMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SkillVO vo = skillService.setStatus("mcp-s1-send_mail", true);

        assertEquals("mcp", vo.getType());
        assertTrue(vo.getPublished());
        ArgumentCaptor<McpToolPublishDO> captor = ArgumentCaptor.forClass(McpToolPublishDO.class);
        verify(mcpToolPublishMapper).insert(captor.capture());
        assertEquals("send_mail", captor.getValue().getToolName(), "虚拟 ID 必须正确拆出工具名");
        assertEquals("ws-1", captor.getValue().getWorkspaceId());
        assertTrue(captor.getValue().getPublished());
    }

    @Test
    void setStatus_mcpServerNotFound_throws5009() {
        when(mcpServerMapper.selectById("ghost")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.setStatus("mcp-ghost-tool", true));
        assertEquals(5009, e.getCode());
    }

    // ---------- invoke（广场调用，动作型两阶段确认） ----------

    private SkillExecutor actionTypeExecutor(String skillId) {
        SkillExecutor executor = mock(SkillExecutor.class);
        when(executor.getDescriptor()).thenReturn(SkillDescriptor.builder()
                .id(skillId).name("邮件发送").type("api").actionType(true).enabled(true).build());
        when(skillRegistry.getExecutor(skillId)).thenReturn(Optional.of(executor));
        return executor;
    }

    @Test
    void invoke_actionTypeWithoutToken_returnsDraftOnly() {
        SkillExecutor executor = actionTypeExecutor("skill-a");
        when(confirmTokenStore.issue(eq("skill-a"), any())).thenReturn("token-1");

        InvokeSkillVO vo = skillService.invoke("skill-a", Map.of("to", "a@x.com"), null);

        assertTrue(vo.isConfirmRequired());
        assertEquals("token-1", vo.getConfirmToken());
        assertEquals(Map.of("to", "a@x.com"), vo.getDraftParams(), "草稿必须回显参数供确认");
        verify(executor, never()).execute(any(), any());
        verifyNoInteractions(callLogRecorder);
    }

    @Test
    void invoke_actionTypeWithValidToken_executesAndRecordsAudit() {
        SkillExecutor executor = actionTypeExecutor("skill-a");
        when(confirmTokenStore.consume("token-1", "skill-a", Map.of("to", "a@x.com")))
                .thenReturn(true);
        when(executor.execute(any(), any()))
                .thenReturn(SkillResult.success(Map.of("sent", true), 5L));

        InvokeSkillVO vo = skillService.invoke("skill-a", Map.of("to", "a@x.com"), "token-1");

        assertFalse(vo.isConfirmRequired());
        assertTrue(vo.getSuccess());
        assertNotNull(vo.getTraceId());
        verify(callLogRecorder).record(eq("ws-1"), eq("plaza"), any(), eq("skill-a"),
                any(), any(), any(), anyLong(), eq(true), any());
    }

    @Test
    void invoke_actionTypeWithInvalidToken_throws5015() {
        SkillExecutor executor = actionTypeExecutor("skill-a");
        when(confirmTokenStore.consume(any(), any(), any())).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> skillService.invoke("skill-a", Map.of(), "bad-token"));
        assertEquals(5015, e.getCode());
        verify(executor, never()).execute(any(), any());
    }

    @Test
    void invoke_nonActionType_executesDirectlyWithoutToken() {
        SkillExecutor executor = mock(SkillExecutor.class);
        when(executor.getDescriptor()).thenReturn(SkillDescriptor.builder()
                .id("skill-p").type("prompt").actionType(false).enabled(true).build());
        when(skillRegistry.getExecutor("skill-p")).thenReturn(Optional.of(executor));
        when(executor.execute(any(), any()))
                .thenReturn(SkillResult.success(Map.of("content", "x"), 1L));

        InvokeSkillVO vo = skillService.invoke("skill-p", null, null);

        assertFalse(vo.isConfirmRequired());
        assertTrue(vo.getSuccess());
        verify(confirmTokenStore, never()).issue(any(), any());
    }
}
