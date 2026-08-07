package com.agentone.skill.service.impl;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.dto.BindSkillDTO;
import com.agentone.skill.entity.AgentSkillBindingDO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.service.AgentSkillService;
import com.agentone.skill.vo.AgentSkillBindingVO;
import com.agentone.skill.vo.SkillVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentone.common.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent-Skill 绑定服务实现。
 *
 * builtin Skill 采用"虚拟挂载"：不落 skill 表，列表/绑定时从 SkillRegistry 合并，
 * 绑定关系仍落 agent_skill_binding（租户拦截器保证隔离）；
 * skill 表只存 api / market 等用户创建的 Skill。
 */
@Service
@RequiredArgsConstructor
public class AgentSkillServiceImpl implements AgentSkillService {

    private final AgentSkillBindingMapper bindingMapper;
    private final SkillMapper skillMapper;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSkillBindingVO bind(BindSkillDTO dto) {
        // 检查是否已绑定
        Long count = bindingMapper.selectCount(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .eq(AgentSkillBindingDO::getAgentId, dto.getAgentId())
                        .eq(AgentSkillBindingDO::getSkillId, dto.getSkillId())
        );
        if (count > 0) {
            throw new BusinessException(5001, "该 Agent 已绑定此 Skill");
        }

        // Skill 存在性：DB 行（api/market）或 Registry 虚拟 builtin
        SkillDO skill = skillMapper.selectById(dto.getSkillId());
        SkillDescriptor builtin = skill == null ? descriptorOf(dto.getSkillId()) : null;
        if (skill == null && builtin == null) {
            throw new BusinessException(5002, "Skill 不存在");
        }

        // S3: 跨租户防护——DB 行 Skill 仅允许绑定当前工作空间的；builtin 全局可见无需校验
        String wsId = RuntimeContext.getWorkspaceId();
        if (skill != null && !wsId.equals(skill.getWorkspaceId())) {
            throw new BusinessException(5004, "无权绑定其他工作空间的 Skill");
        }

        // 创建绑定
        AgentSkillBindingDO binding = new AgentSkillBindingDO();
        binding.setWorkspaceId(wsId);
        binding.setAgentId(dto.getAgentId());
        binding.setSkillId(dto.getSkillId());
        binding.setSkillVersion(dto.getSkillVersion() != null ? dto.getSkillVersion()
                : skill != null ? skill.getVersion() : builtin.getVersion());
        binding.setConfigOverride(dto.getConfigOverride() != null ? dto.getConfigOverride() : "{}");
        binding.setEnabled(true);
        binding.setCreatedAt(LocalDateTime.now());

        bindingMapper.insert(binding);

        return toVO(binding, skill);
    }

    @Override
    public void unbind(String bindingId) {
        bindingMapper.deleteById(bindingId);
    }

    @Override
    public List<AgentSkillBindingVO> listBindings(String agentId) {
        List<AgentSkillBindingDO> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .eq(AgentSkillBindingDO::getAgentId, agentId)
                        .orderByDesc(AgentSkillBindingDO::getCreatedAt)
        );

        return bindings.stream().map(binding -> {
            SkillDO skill = skillMapper.selectById(binding.getSkillId());
            return toVO(binding, skill);
        }).collect(Collectors.toList());
    }

    @Override
    public void removeAllBindings(String agentId) {
        bindingMapper.delete(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .eq(AgentSkillBindingDO::getAgentId, agentId)
        );
    }

    @Override
    public void toggleEnabled(String bindingId, boolean enabled) {
        AgentSkillBindingDO binding = bindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new BusinessException(5003, "绑定记录不存在");
        }
        binding.setEnabled(enabled);
        bindingMapper.updateById(binding);
    }

    @Override
    public PageResult<SkillVO> listSkills(String workspaceId, Integer page, Integer size) {
        Page<SkillDO> p = new Page<>(page, size);
        Page<SkillDO> result = skillMapper.selectPage(p,
                new LambdaQueryWrapper<SkillDO>()
                        .eq(SkillDO::getWorkspaceId, workspaceId)
                        .eq(SkillDO::getStatus, "active")
                        .orderByDesc(SkillDO::getInstalledAt));

        List<SkillVO> records = result.getRecords().stream().map(this::toSkillVO)
                .collect(Collectors.toList());
        long total = result.getTotal();

        // builtin 虚拟挂载：第一页置顶合并
        List<SkillVO> builtins = builtinVOs();
        if (page == 1 && !builtins.isEmpty()) {
            List<SkillVO> merged = new ArrayList<>(builtins);
            merged.addAll(records);
            records = merged;
            total += builtins.size();
        }

        Page<SkillVO> voPage = new Page<>(result.getCurrent(), result.getSize(), total);
        voPage.setRecords(records);
        return PageResult.of(voPage);
    }

    /** 从 Registry 合成 builtin Skill 的 VO（不落库） */
    private List<SkillVO> builtinVOs() {
        return skillRegistry.listDescriptors().stream()
                .filter(d -> "builtin".equals(d.getType()))
                .map(d -> {
                    SkillVO vo = new SkillVO();
                    vo.setId(d.getId());
                    vo.setName(d.getName());
                    vo.setType("builtin");
                    vo.setSource(d.getSource());
                    vo.setDescription(d.getDescription());
                    vo.setInputSchema(toJson(d.getInputSchema()));
                    vo.setOutputSchema(toJson(d.getOutputSchema()));
                    vo.setConfig("{}");
                    vo.setVersion(d.getVersion());
                    vo.setStatus("active");
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private SkillDescriptor descriptorOf(String skillId) {
        return skillRegistry.getExecutor(skillId)
                .map(SkillExecutor::getDescriptor)
                .orElse(null);
    }

    private AgentSkillBindingVO toVO(AgentSkillBindingDO binding, SkillDO skill) {
        AgentSkillBindingVO vo = new AgentSkillBindingVO();
        BeanUtils.copyProperties(binding, vo);
        if (skill != null) {
            vo.setSkillName(skill.getName());
            vo.setSkillType(skill.getType());
        } else {
            // builtin 虚拟挂载：名称/类型从 Registry 取
            SkillDescriptor descriptor = descriptorOf(binding.getSkillId());
            if (descriptor != null) {
                vo.setSkillName(descriptor.getName());
                vo.setSkillType(descriptor.getType());
            }
        }
        return vo;
    }

    private SkillVO toSkillVO(SkillDO skill) {
        SkillVO vo = new SkillVO();
        BeanUtils.copyProperties(skill, vo);
        return vo;
    }

    private String toJson(Object schema) {
        if (schema == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }
}
