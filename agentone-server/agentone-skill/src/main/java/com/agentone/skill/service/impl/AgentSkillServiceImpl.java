package com.agentone.skill.service.impl;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent-Skill 绑定服务实现
 */
@Service
@RequiredArgsConstructor
public class AgentSkillServiceImpl implements AgentSkillService {

    private final AgentSkillBindingMapper bindingMapper;
    private final SkillMapper skillMapper;

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

        // 检查 Skill 是否存在
        SkillDO skill = skillMapper.selectById(dto.getSkillId());
        if (skill == null) {
            throw new BusinessException(5002, "Skill 不存在");
        }

        // S3: 跨租户防护——仅允许绑定当前工作空间的 Skill
        String wsId = RuntimeContext.getWorkspaceId();
        if (!wsId.equals(skill.getWorkspaceId())) {
            throw new BusinessException(5004, "无权绑定其他工作空间的 Skill");
        }

        // 创建绑定
        AgentSkillBindingDO binding = new AgentSkillBindingDO();
        binding.setWorkspaceId(wsId);
        binding.setAgentId(dto.getAgentId());
        binding.setSkillId(dto.getSkillId());
        binding.setSkillVersion(dto.getSkillVersion() != null ? dto.getSkillVersion() : skill.getVersion());
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
        Page<SkillVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toSkillVO).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    private AgentSkillBindingVO toVO(AgentSkillBindingDO binding, SkillDO skill) {
        AgentSkillBindingVO vo = new AgentSkillBindingVO();
        BeanUtils.copyProperties(binding, vo);
        if (skill != null) {
            vo.setSkillName(skill.getName());
            vo.setSkillType(skill.getType());
        }
        return vo;
    }

    private SkillVO toSkillVO(SkillDO skill) {
        SkillVO vo = new SkillVO();
        BeanUtils.copyProperties(skill, vo);
        return vo;
    }
}
