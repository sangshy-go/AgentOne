package com.agentone.skill.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.core.UrlSafetyUtil;
import com.agentone.skill.dto.SkillDTO;
import com.agentone.skill.entity.AgentSkillBindingDO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.executor.ApiSkillExecutor;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.service.SkillService;
import com.agentone.skill.vo.SkillVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Skill 中心服务实现。
 *
 * 只管理 api 模式 Skill（用户在 Skill 中心创建的 HTTP API 封装）：
 * - DB 落 skill 表（租户拦截器自动隔离）
 * - 每次写操作同步维护 SkillRegistry，保证 Agent 对话侧立即可用
 * - config 中的 url 在创建/更新时做 SSRF 校验（ApiSkillExecutor 执行时二次校验）
 */
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SkillMapper skillMapper;
    private final AgentSkillBindingMapper bindingMapper;
    private final SkillRegistry skillRegistry;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO create(SkillDTO dto) {
        validatePayload(dto);

        SkillDO skill = new SkillDO();
        skill.setWorkspaceId(RuntimeContext.getWorkspaceId());
        skill.setName(dto.getName());
        skill.setType("api");
        skill.setSource("custom");
        skill.setDescription(dto.getDescription());
        skill.setInputSchema(defaultEmpty(dto.getInputSchema()));
        skill.setOutputSchema(defaultEmpty(dto.getOutputSchema()));
        skill.setConfig(dto.getConfig());
        skill.setVersion(dto.getVersion() != null && !dto.getVersion().isBlank()
                ? dto.getVersion() : "1.0.0");
        skill.setStatus("active");
        skill.setInstalledAt(LocalDateTime.now());
        skillMapper.insert(skill);

        // 注册执行器：创建成功后 Agent 立即可绑定、可对话调用
        skillRegistry.register(new ApiSkillExecutor(skill, webClient, objectMapper));
        return toVO(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO update(String skillId, SkillDTO dto) {
        SkillDO skill = requireApiSkill(skillId);
        validatePayload(dto);

        skill.setName(dto.getName());
        skill.setDescription(dto.getDescription());
        skill.setInputSchema(defaultEmpty(dto.getInputSchema()));
        skill.setOutputSchema(defaultEmpty(dto.getOutputSchema()));
        skill.setConfig(dto.getConfig());
        if (dto.getVersion() != null && !dto.getVersion().isBlank()) {
            skill.setVersion(dto.getVersion());
        }
        skillMapper.updateById(skill);

        // 刷新执行器：Registry 以 skillId 为 key，register 即覆盖
        skillRegistry.register(new ApiSkillExecutor(skill, webClient, objectMapper));
        return toVO(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String skillId) {
        requireApiSkill(skillId);

        Long bindingCount = bindingMapper.selectCount(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .eq(AgentSkillBindingDO::getSkillId, skillId));
        if (bindingCount > 0) {
            throw new BusinessException(5005, "该 Skill 仍被 Agent 绑定，请先解除绑定");
        }

        skillMapper.deleteById(skillId);
        skillRegistry.unregister(skillId);
    }

    @Override
    public SkillResult test(String skillId, Map<String, Object> params) {
        // 优先用 Registry 中的执行器；不在 Registry（如已停用）时从 DB 行临时构建
        SkillExecutor executor = skillRegistry.getExecutor(skillId).orElse(null);
        if (executor == null) {
            SkillDO skill = skillMapper.selectById(skillId);
            if (skill == null) {
                throw new BusinessException(5002, "Skill 不存在");
            }
            if (!"api".equals(skill.getType())) {
                throw new BusinessException(5006, "该 Skill 不支持直接测试");
            }
            executor = new ApiSkillExecutor(skill, webClient, objectMapper);
        }

        SkillInvocation invocation = SkillInvocation.builder()
                .skillId(skillId)
                .params(params != null ? params : Map.of())
                .traceId("test-" + UUID.randomUUID().toString().substring(0, 8))
                .build();
        Context context = Context.of(RuntimeContext.getUserId(), RuntimeContext.getWorkspaceId());
        return executor.execute(invocation, context);
    }

    /** 校验 config / schema 合法性：config 必须为含 url 的 JSON 且通过 SSRF 检查 */
    private void validatePayload(SkillDTO dto) {
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(dto.getConfig(), MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException(5007, "Skill 配置不是合法 JSON");
        }
        Object url = config.get("url");
        if (url == null || url.toString().isBlank()) {
            throw new BusinessException(5007, "Skill 配置缺少 url");
        }
        try {
            UrlSafetyUtil.validate(url.toString());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(5008, "Skill 地址不安全: " + e.getMessage());
        }
        validateSchemaJson(dto.getInputSchema(), "inputSchema");
        validateSchemaJson(dto.getOutputSchema(), "outputSchema");
    }

    private void validateSchemaJson(String json, String field) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException(5007, field + " 不是合法 JSON");
        }
    }

    /** 加载 skill 行并要求是 api 类型；租户拦截器保证查不到其他工作空间的数据 */
    private SkillDO requireApiSkill(String skillId) {
        SkillDO skill = skillMapper.selectById(skillId);
        if (skill == null) {
            throw new BusinessException(5002, "Skill 不存在");
        }
        if (!"api".equals(skill.getType())) {
            throw new BusinessException(5006, "内置 Skill 不可修改");
        }
        return skill;
    }

    private String defaultEmpty(String value) {
        return value != null && !value.isBlank() ? value : "{}";
    }

    private SkillVO toVO(SkillDO skill) {
        SkillVO vo = new SkillVO();
        BeanUtils.copyProperties(skill, vo);
        return vo;
    }
}
