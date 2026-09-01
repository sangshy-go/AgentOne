package com.agentone.agent.service.impl;

import com.agentone.agent.dto.AgentDTO;
import com.agentone.agent.dto.AgentUpdateDTO;
import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.entity.ChatMessageDO;
import com.agentone.agent.entity.ChatSessionDO;
import com.agentone.agent.enums.AgentAction;
import com.agentone.agent.enums.AgentStatus;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.ChatMessageMapper;
import com.agentone.agent.mapper.ChatSessionMapper;
import com.agentone.agent.service.AgentService;
import com.agentone.agent.vo.AgentVO;
import com.agentone.common.context.RuntimeContext;
import com.agentone.knowledge.service.KnowledgeService;
import com.agentone.skill.service.AgentSkillService;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.ResultCode;
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
 * Agent 管理服务实现
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AgentSkillService agentSkillService;
    private final KnowledgeService knowledgeService;

    @Override
    public PageResult<AgentVO> list(Integer page, Integer size) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        // 已归档为历史遗留状态（删除已改为物理删除），不在默认列表中展示
        Page<AgentDO> p = new Page<>(page, size);
        Page<AgentDO> result = agentMapper.selectPage(p,
                new LambdaQueryWrapper<AgentDO>()
                        .eq(AgentDO::getWorkspaceId, workspaceId)
                        .ne(AgentDO::getStatus, AgentStatus.ARCHIVED)
                        .orderByDesc(AgentDO::getUpdatedAt));
        Page<AgentVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    public AgentVO getById(String id) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        AgentDO agent = agentMapper.selectOne(
                new LambdaQueryWrapper<AgentDO>()
                        .eq(AgentDO::getId, id)
                        .eq(AgentDO::getWorkspaceId, workspaceId)
        );
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        return toVO(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentVO create(AgentDTO dto) {
        AgentDO agent = new AgentDO();
        BeanUtils.copyProperties(dto, agent);
        agent.setWorkspaceId(RuntimeContext.getWorkspaceId());
        agent.setStatus(AgentStatus.DRAFT);
        agent.setCurrentVersion(1);
        agent.setCreatedBy(RuntimeContext.getUserId());
        agent.setCreatedAt(LocalDateTime.now());
        agent.setUpdatedAt(LocalDateTime.now());

        // 默认配置
        if (agent.getModelConfig() == null) agent.setModelConfig("{}");
        if (agent.getMemoryConfig() == null) agent.setMemoryConfig("{}");
        if (agent.getAdvancedConfig() == null) agent.setAdvancedConfig("{}");

        agentMapper.insert(agent);
        return toVO(agent);
    }

    @Override
    public AgentVO update(String id, AgentUpdateDTO dto) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        AgentDO agent = agentMapper.selectOne(
                new LambdaQueryWrapper<AgentDO>()
                        .eq(AgentDO::getId, id)
                        .eq(AgentDO::getWorkspaceId, workspaceId)
        );
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        // 只有草稿和测试中可以修改
        if (!agent.getStatus().isEditable()) {
            String msg = agent.getStatus() == AgentStatus.PENDING_REVIEW
                    ? "审批中的 Agent 不可编辑（审什么 = 发什么），如需修改请先撤回申请"
                    : "已发布的 Agent 不能直接修改，请先退回草稿";
            throw new BusinessException(3002, msg);
        }

        if (dto.getName() != null) agent.setName(dto.getName());
        if (dto.getDescription() != null) agent.setDescription(dto.getDescription());
        if (dto.getCategory() != null) agent.setCategory(dto.getCategory());
        if (dto.getIcon() != null) agent.setIcon(dto.getIcon());
        if (dto.getAvatarUrl() != null) agent.setAvatarUrl(dto.getAvatarUrl());
        if (dto.getAgentsMd() != null) agent.setAgentsMd(dto.getAgentsMd());
        if (dto.getModelConfig() != null) agent.setModelConfig(dto.getModelConfig());
        if (dto.getModelProviderId() != null) agent.setModelProviderId(dto.getModelProviderId());
        if (dto.getMemoryConfig() != null) agent.setMemoryConfig(dto.getMemoryConfig());
        if (dto.getAdvancedConfig() != null) agent.setAdvancedConfig(dto.getAdvancedConfig());
        agent.setUpdatedAt(LocalDateTime.now());

        agentMapper.updateById(agent);
        return toVO(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        AgentDO agent = agentMapper.selectOne(
                new LambdaQueryWrapper<AgentDO>()
                        .eq(AgentDO::getId, id)
                        .eq(AgentDO::getWorkspaceId, workspaceId)
        );
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        // 审批中的 Agent 冻结：先撤回申请或完成审批，才允许删除（防审批单悬挂指向已删 Agent）
        if (agent.getStatus() == AgentStatus.PENDING_REVIEW) {
            throw new BusinessException(3004, "该 Agent 正在审批中，请先撤回申请或完成审批后再删除");
        }

        // 级联物理删除：对话消息 → 会话 → Skill 绑定 → 知识库绑定 → Agent 本体
        // 1. 查出该 Agent 的全部会话 ID
        List<String> sessionIds = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSessionDO>()
                        .eq(ChatSessionDO::getAgentId, id)
                        .select(ChatSessionDO::getId)
        ).stream().map(ChatSessionDO::getId).collect(Collectors.toList());
        // 2. 按会话 ID 批量删除消息（消息表无 agentId，只能经由 sessionId）
        if (!sessionIds.isEmpty()) {
            messageMapper.delete(
                    new LambdaQueryWrapper<ChatMessageDO>()
                            .in(ChatMessageDO::getSessionId, sessionIds)
            );
        }
        // 3. 删除会话
        sessionMapper.delete(
                new LambdaQueryWrapper<ChatSessionDO>()
                        .eq(ChatSessionDO::getAgentId, id)
        );
        // 4. 删除跨模块绑定（各自按 agentId 批量删除，避免循环调库）
        agentSkillService.removeAllBindings(id);
        knowledgeService.removeAllBindings(id);
        // 5. 删除 Agent 本体
        agentMapper.deleteById(id);
    }

    @Override
    public AgentVO stop(String id) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        AgentDO agent = agentMapper.selectOne(
                new LambdaQueryWrapper<AgentDO>()
                        .eq(AgentDO::getId, id)
                        .eq(AgentDO::getWorkspaceId, workspaceId)
        );
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        agent.setStatus(agent.getStatus().transition(AgentAction.STOP));
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        return toVO(agent);
    }

    @Override
    public AgentVO revertToDraft(String id) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        AgentDO agent = agentMapper.selectOne(
                new LambdaQueryWrapper<AgentDO>()
                        .eq(AgentDO::getId, id)
                        .eq(AgentDO::getWorkspaceId, workspaceId)
        );
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        agent.setStatus(agent.getStatus().transition(AgentAction.REVERT_TO_DRAFT));
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        return toVO(agent);
    }

    private AgentVO toVO(AgentDO agent) {
        AgentVO vo = new AgentVO();
        BeanUtils.copyProperties(agent, vo);
        return vo;
    }
}
