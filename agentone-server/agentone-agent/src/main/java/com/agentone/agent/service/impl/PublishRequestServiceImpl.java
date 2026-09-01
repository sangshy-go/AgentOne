package com.agentone.agent.service.impl;

import com.agentone.agent.dto.RejectPublishRequestDTO;
import com.agentone.agent.dto.SubmitPublishRequestDTO;
import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.entity.AgentPublishRequestDO;
import com.agentone.agent.enums.AgentAction;
import com.agentone.agent.enums.AgentStatus;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.AgentPublishRequestMapper;
import com.agentone.agent.service.PublishRequestService;
import com.agentone.agent.vo.PublishRequestVO;
import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.PageResult;
import com.agentone.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 发布审批服务实现（课题⑩）
 *
 * 双人原则落地：
 * 1. approve/reject 硬校验 审核人 != 提交人（8003）
 * 2. reject 的驳回理由必填（8004）——审计时「为什么被拒」和「谁批准的」同样重要
 * 3. 审批通过时版本号+1 从 publish() 迁入本流程（发布唯一路径 = 审批）
 */
@Service
@RequiredArgsConstructor
public class PublishRequestServiceImpl implements PublishRequestService {

    /** 可审批角色（auditor 只读可查，不可审批） */
    private static final Set<String> APPROVER_ROLES = Set.of("admin", "owner");

    /** 可提交发布的角色（写操作，observer/auditor 只读不可提交） */
    private static final Set<String> SUBMITTER_ROLES = Set.of("developer", "admin", "owner");

    /** 可提交发布的 Agent 状态 */
    private static final Set<AgentStatus> SUBMITTABLE_STATUS = Set.of(AgentStatus.DRAFT, AgentStatus.TESTING);

    private final AgentPublishRequestMapper requestMapper;
    private final AgentMapper agentMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishRequestVO submit(SubmitPublishRequestDTO dto) {
        // 纵深防御：observer/auditor 的写请求已被 WorkspaceRbacFilter 拦截，此处兜底
        String role = RuntimeContext.getRole();
        if (role == null || !SUBMITTER_ROLES.contains(role)) {
            throw new BusinessException(8006, "当前角色无权提交发布审批");
        }
        String workspaceId = RuntimeContext.getWorkspaceId();
        AgentDO agent = findAgent(dto.getAgentId(), workspaceId);
        if (!SUBMITTABLE_STATUS.contains(agent.getStatus())) {
            throw new BusinessException(8005, "仅草稿/测试中状态的 Agent 可提交发布审批");
        }
        Long pendingCount = requestMapper.selectCount(
                new LambdaQueryWrapper<AgentPublishRequestDO>()
                        .eq(AgentPublishRequestDO::getAgentId, agent.getId())
                        .eq(AgentPublishRequestDO::getStatus, STATUS_PENDING));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException(8002, "该 Agent 已有待审的发布申请，请勿重复提交");
        }

        AgentPublishRequestDO request = new AgentPublishRequestDO();
        request.setWorkspaceId(workspaceId);
        request.setAgentId(agent.getId());
        request.setAgentName(agent.getName());
        request.setConfigSnapshot(buildConfigSnapshot(agent));
        request.setStatus(STATUS_PENDING);
        request.setSubmitterId(RuntimeContext.getUserId());
        Context ctx = RuntimeContext.get();
        request.setSubmitterEmail(ctx != null ? ctx.getEmail() : null);
        request.setSubmittedAt(LocalDateTime.now());
        requestMapper.insert(request);

        // Agent 进入审批中：冻结编辑/删除，保证「审什么 = 发什么」
        agent.setStatus(agent.getStatus().transition(AgentAction.SUBMIT_REVIEW));
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);

        return toVO(request, agent);
    }

    @Override
    public PageResult<PublishRequestVO> list(String status, String agentId, Integer page, Integer size) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        LambdaQueryWrapper<AgentPublishRequestDO> qw = new LambdaQueryWrapper<AgentPublishRequestDO>()
                .eq(AgentPublishRequestDO::getWorkspaceId, workspaceId);
        // developer/observer 只能看自己提交的；admin/owner/auditor 看全空间
        String role = RuntimeContext.getRole();
        if (!APPROVER_ROLES.contains(role) && !"auditor".equals(role)) {
            qw.eq(AgentPublishRequestDO::getSubmitterId, RuntimeContext.getUserId());
        }
        if (status != null && !status.isBlank()) {
            qw.eq(AgentPublishRequestDO::getStatus, status);
        }
        if (agentId != null && !agentId.isBlank()) {
            qw.eq(AgentPublishRequestDO::getAgentId, agentId);
        }
        qw.orderByDesc(AgentPublishRequestDO::getSubmittedAt);

        Page<AgentPublishRequestDO> result = requestMapper.selectPage(new Page<>(page, size), qw);

        // 批量富化 Agent 当前状态（避免循环单查）
        List<String> agentIds = result.getRecords().stream()
                .map(AgentPublishRequestDO::getAgentId).distinct().collect(Collectors.toList());
        Map<String, AgentDO> agentMap = agentIds.isEmpty() ? Map.of()
                : agentMapper.selectList(new LambdaQueryWrapper<AgentDO>()
                        .eq(AgentDO::getWorkspaceId, workspaceId)
                        .in(AgentDO::getId, agentIds))
                .stream().collect(Collectors.toMap(AgentDO::getId, Function.identity()));

        Page<PublishRequestVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(r -> toVO(r, agentMap.get(r.getAgentId())))
                .collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishRequestVO approve(String id) {
        AgentPublishRequestDO request = getPendingOrThrow(id);
        if (!APPROVER_ROLES.contains(RuntimeContext.getRole())) {
            throw new BusinessException(8007, "仅管理员或所有者可审批发布申请");
        }
        if (request.getSubmitterId().equals(RuntimeContext.getUserId())) {
            throw new BusinessException(8003, "双人原则：提交人不能审批自己的发布申请");
        }
        AgentDO agent = findAgentOrNull(request.getAgentId(), request.getWorkspaceId());
        if (agent == null || agent.getStatus() == AgentStatus.ARCHIVED) {
            throw new BusinessException(8009, "Agent 已被删除或归档，该发布申请已失效");
        }

        agent.setStatus(agent.getStatus().transition(AgentAction.APPROVE));
        // NPE 防御：currentVersion 可能为 null
        int currentVer = agent.getCurrentVersion() != null ? agent.getCurrentVersion() : 0;
        agent.setCurrentVersion(currentVer + 1);
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);

        markReviewed(request, STATUS_APPROVED);
        return toVO(request, agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishRequestVO reject(String id, RejectPublishRequestDTO dto) {
        AgentPublishRequestDO request = getPendingOrThrow(id);
        if (!APPROVER_ROLES.contains(RuntimeContext.getRole())) {
            throw new BusinessException(8007, "仅管理员或所有者可审批发布申请");
        }
        if (request.getSubmitterId().equals(RuntimeContext.getUserId())) {
            throw new BusinessException(8003, "双人原则：提交人不能审批自己的发布申请");
        }
        if (dto == null || dto.getComment() == null || dto.getComment().isBlank()) {
            throw new BusinessException(8004, "驳回必须填写理由");
        }
        AgentDO agent = findAgentOrNull(request.getAgentId(), request.getWorkspaceId());
        if (agent == null || agent.getStatus() == AgentStatus.ARCHIVED) {
            throw new BusinessException(8009, "Agent 已被删除或归档，该发布申请已失效");
        }

        agent.setStatus(agent.getStatus().transition(AgentAction.REJECT));
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);

        request.setReviewComment(dto.getComment().trim());
        markReviewed(request, STATUS_REJECTED);
        return toVO(request, agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishRequestVO withdraw(String id) {
        AgentPublishRequestDO request = getPendingOrThrow(id);
        if (!request.getSubmitterId().equals(RuntimeContext.getUserId())) {
            throw new BusinessException(8008, "仅提交人本人可撤回发布申请");
        }
        AgentDO agent = findAgentOrNull(request.getAgentId(), request.getWorkspaceId());
        if (agent == null || agent.getStatus() == AgentStatus.ARCHIVED) {
            throw new BusinessException(8009, "Agent 已被删除或归档，该发布申请已失效");
        }

        agent.setStatus(agent.getStatus().transition(AgentAction.WITHDRAW));
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);

        request.setStatus(STATUS_WITHDRAWN);
        requestMapper.updateById(request);
        return toVO(request, agent);
    }

    // ---------------- 私有辅助 ----------------

    /** 申请不存在（8001）或非待审状态（复用 8001，消息区分）*/
    private AgentPublishRequestDO getPendingOrThrow(String id) {
        AgentPublishRequestDO request = requestMapper.selectOne(
                new LambdaQueryWrapper<AgentPublishRequestDO>()
                        .eq(AgentPublishRequestDO::getId, id));
        if (request == null) {
            throw new BusinessException(8001, "发布申请不存在");
        }
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException(8001, "该发布申请已处理，当前状态: " + request.getStatus());
        }
        return request;
    }

    private AgentDO findAgent(String agentId, String workspaceId) {
        AgentDO agent = findAgentOrNull(agentId, workspaceId);
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    private AgentDO findAgentOrNull(String agentId, String workspaceId) {
        return agentMapper.selectOne(
                new LambdaQueryWrapper<AgentDO>()
                        .eq(AgentDO::getId, agentId)
                        .eq(AgentDO::getWorkspaceId, workspaceId));
    }

    /** 审核落款：审核人/邮箱/时间 + 状态，一次 update */
    private void markReviewed(AgentPublishRequestDO request, String status) {
        request.setStatus(status);
        request.setReviewerId(RuntimeContext.getUserId());
        Context ctx = RuntimeContext.get();
        request.setReviewerEmail(ctx != null ? ctx.getEmail() : null);
        request.setReviewedAt(LocalDateTime.now());
        requestMapper.updateById(request);
    }

    /**
     * 配置快照：仅序列化配置字段（剔除 status/createdAt 等运行时字段），
     * 审批冻结期间配置不可变，快照与发布内容一致
     */
    private String buildConfigSnapshot(AgentDO agent) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", agent.getName());
        snapshot.put("description", agent.getDescription());
        snapshot.put("category", agent.getCategory());
        snapshot.put("icon", agent.getIcon());
        snapshot.put("avatarUrl", agent.getAvatarUrl());
        snapshot.put("agentsMd", agent.getAgentsMd());
        snapshot.put("modelConfig", agent.getModelConfig());
        snapshot.put("modelProviderId", agent.getModelProviderId());
        snapshot.put("memoryConfig", agent.getMemoryConfig());
        snapshot.put("advancedConfig", agent.getAdvancedConfig());
        snapshot.put("currentVersion", agent.getCurrentVersion());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "{}";
        }
    }

    private PublishRequestVO toVO(AgentPublishRequestDO request, AgentDO agent) {
        PublishRequestVO vo = new PublishRequestVO();
        vo.setId(request.getId());
        vo.setAgentId(request.getAgentId());
        vo.setAgentName(request.getAgentName());
        vo.setAgentStatus(agent != null ? agent.getStatus() : null);
        vo.setStatus(request.getStatus());
        vo.setSubmitterId(request.getSubmitterId());
        vo.setSubmitterEmail(request.getSubmitterEmail());
        vo.setReviewerId(request.getReviewerId());
        vo.setReviewerEmail(request.getReviewerEmail());
        vo.setReviewComment(request.getReviewComment());
        vo.setSubmittedAt(request.getSubmittedAt());
        vo.setReviewedAt(request.getReviewedAt());
        return vo;
    }
}
