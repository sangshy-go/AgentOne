package com.agentone.skill.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.dto.SkillDTO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.vo.SkillVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

        verify(skillMapper).deleteById("skill-1");
        verify(skillRegistry).unregister("skill-1");
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
        when(skillRegistry.getExecutor("skill-1")).thenReturn(Optional.of(executor));

        SkillResult result = skillService.test("skill-1", Map.of("city", "北京"));

        assertTrue(result.isSuccess());
        assertEquals(true, result.getData().get("ok"));
        verify(skillMapper, never()).selectById(any());
    }

    @Test
    void test_registryMissButDbHit_buildsExecutorOffline() {
        // Registry 无执行器（如已停用），但 DB 行存在 → 临时构建执行器。
        // url 使用无法解析的主机名：UrlSafetyUtil 直接拒绝，不会发起真实请求。
        SkillDO skill = new SkillDO();
        skill.setId("skill-1");
        skill.setType("api");
        skill.setStatus("disabled");
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
}
