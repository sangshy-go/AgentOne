package com.agentone.agent.service.impl;

import com.agentone.agent.dto.RejectPublishRequestDTO;
import com.agentone.agent.dto.SubmitPublishRequestDTO;
import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.entity.AgentPublishRequestDO;
import com.agentone.agent.enums.AgentStatus;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.AgentPublishRequestMapper;
import com.agentone.agent.vo.PublishRequestVO;
import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PublishRequestServiceImpl 单测（课题⑩）：
 * 提交/审批/驳回/撤回全分支 + 双人原则（8003）+ 驳回理由必填（8004）。
 */
@ExtendWith(MockitoExtension.class)
class PublishRequestServiceImplTest {

    @Mock
    private AgentPublishRequestMapper requestMapper;

    @Mock
    private AgentMapper agentMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PublishRequestServiceImpl service;

    private static final String WS = "ws-1";
    private static final String SUBMITTER = "user-dev";
    private static final String APPROVER = "user-admin";

    @BeforeEach
    void setUp() {
        Context ctx = Context.of(SUBMITTER, WS);
        ctx.setEmail("dev@agentone.local");
        ctx.setRole("developer");
        RuntimeContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    // ---------------- 测试数据构造 ----------------

    private AgentDO draftAgent() {
        AgentDO agent = new AgentDO();
        agent.setId("agent-1");
        agent.setWorkspaceId(WS);
        agent.setName("测试 Agent");
        agent.setStatus(AgentStatus.DRAFT);
        agent.setCurrentVersion(1);
        agent.setModelConfig("{}");
        return agent;
    }

    private AgentPublishRequestDO pendingRequest() {
        AgentPublishRequestDO request = new AgentPublishRequestDO();
        request.setId("req-1");
        request.setWorkspaceId(WS);
        request.setAgentId("agent-1");
        request.setAgentName("测试 Agent");
        request.setStatus("pending");
        request.setSubmitterId(SUBMITTER);
        request.setSubmitterEmail("dev@agentone.local");
        return request;
    }

    private void loginAsApprover() {
        Context ctx = Context.of(APPROVER, WS);
        ctx.setEmail("admin@agentone.local");
        ctx.setRole("admin");
        RuntimeContext.set(ctx);
    }

    private SubmitPublishRequestDTO submitDTO() {
        SubmitPublishRequestDTO dto = new SubmitPublishRequestDTO();
        dto.setAgentId("agent-1");
        return dto;
    }

    // ---------------- submit ----------------

    @Test
    void submit_draft_success_andAgentFrozen() {
        when(agentMapper.selectOne(any())).thenReturn(draftAgent());
        when(requestMapper.selectCount(any())).thenReturn(0L);

        PublishRequestVO vo = service.submit(submitDTO());

        assertEquals("pending", vo.getStatus());
        assertEquals(SUBMITTER, vo.getSubmitterId());
        assertEquals("dev@agentone.local", vo.getSubmitterEmail());
        assertEquals(AgentStatus.PENDING_REVIEW, vo.getAgentStatus(), "提交后 Agent 应进入审批中");

        ArgumentCaptor<AgentPublishRequestDO> reqCaptor = ArgumentCaptor.forClass(AgentPublishRequestDO.class);
        verify(requestMapper).insert(reqCaptor.capture());
        assertNotNull(reqCaptor.getValue().getConfigSnapshot(), "必须落配置快照");
        assertEquals(WS, reqCaptor.getValue().getWorkspaceId());

        ArgumentCaptor<AgentDO> agentCaptor = ArgumentCaptor.forClass(AgentDO.class);
        verify(agentMapper).updateById(agentCaptor.capture());
        assertEquals(AgentStatus.PENDING_REVIEW, agentCaptor.getValue().getStatus());
    }

    @Test
    void submit_duplicatePending_throws8002() {
        when(agentMapper.selectOne(any())).thenReturn(draftAgent());
        when(requestMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.submit(submitDTO()));
        assertEquals(8002, e.getCode());
        verify(requestMapper, never()).insert(any(AgentPublishRequestDO.class));
    }

    @Test
    void submit_publishedAgent_throws8005() {
        AgentDO agent = draftAgent();
        agent.setStatus(AgentStatus.PUBLISHED);
        when(agentMapper.selectOne(any())).thenReturn(agent);

        BusinessException e = assertThrows(BusinessException.class, () -> service.submit(submitDTO()));
        assertEquals(8005, e.getCode());
    }

    @Test
    void submit_auditorRole_throws8006() {
        Context ctx = Context.of("user-auditor", WS);
        ctx.setRole("auditor");
        RuntimeContext.set(ctx);

        BusinessException e = assertThrows(BusinessException.class, () -> service.submit(submitDTO()));
        assertEquals(8006, e.getCode());
    }

    @Test
    void submit_agentNotFound_throws3001() {
        when(agentMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.submit(submitDTO()));
        assertEquals(3001, e.getCode());
    }

    // ---------------- approve ----------------

    @Test
    void approve_success_publishesAndBumpsVersion() {
        loginAsApprover();
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());
        AgentDO agent = draftAgent();
        agent.setStatus(AgentStatus.PENDING_REVIEW);
        agent.setCurrentVersion(2);
        when(agentMapper.selectOne(any())).thenReturn(agent);

        PublishRequestVO vo = service.approve("req-1");

        assertEquals("approved", vo.getStatus());
        assertEquals(APPROVER, vo.getReviewerId());
        assertEquals(AgentStatus.PUBLISHED, vo.getAgentStatus());

        ArgumentCaptor<AgentDO> agentCaptor = ArgumentCaptor.forClass(AgentDO.class);
        verify(agentMapper).updateById(agentCaptor.capture());
        assertEquals(AgentStatus.PUBLISHED, agentCaptor.getValue().getStatus());
        assertEquals(3, agentCaptor.getValue().getCurrentVersion(), "审批通过时版本号 +1");

        ArgumentCaptor<AgentPublishRequestDO> reqCaptor = ArgumentCaptor.forClass(AgentPublishRequestDO.class);
        verify(requestMapper).updateById(reqCaptor.capture());
        assertEquals("approved", reqCaptor.getValue().getStatus());
        assertNotNull(reqCaptor.getValue().getReviewedAt());
    }

    @Test
    void approve_selfReview_throws8003() {
        // 双人原则核心：提交人 == 审批人 → 拒绝
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());
        RuntimeContext.get().setRole("admin");  // 提交人同时是 admin 也不行

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve("req-1"));
        assertEquals(8003, e.getCode());
        verify(agentMapper, never()).updateById(any(AgentDO.class));
    }

    @Test
    void approve_developerRole_throws8007() {
        // developer 角色（setUp 默认）无审批权
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve("req-1"));
        assertEquals(8007, e.getCode());
    }

    @Test
    void approve_agentDeleted_throws8009() {
        loginAsApprover();
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());
        when(agentMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve("req-1"));
        assertEquals(8009, e.getCode());
    }

    @Test
    void approve_notPending_throws8001() {
        loginAsApprover();
        AgentPublishRequestDO approved = pendingRequest();
        approved.setStatus("approved");
        when(requestMapper.selectOne(any())).thenReturn(approved);

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve("req-1"));
        assertEquals(8001, e.getCode());
    }

    @Test
    void approve_notFound_throws8001() {
        loginAsApprover();
        when(requestMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.approve("req-x"));
        assertEquals(8001, e.getCode());
    }

    // ---------------- reject ----------------

    @Test
    void reject_success_backToDraft() {
        loginAsApprover();
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());
        AgentDO agent = draftAgent();
        agent.setStatus(AgentStatus.PENDING_REVIEW);
        when(agentMapper.selectOne(any())).thenReturn(agent);
        RejectPublishRequestDTO dto = new RejectPublishRequestDTO();
        dto.setComment("提示词存在合规风险，请修改");

        PublishRequestVO vo = service.reject("req-1", dto);

        assertEquals("rejected", vo.getStatus());
        assertEquals(AgentStatus.DRAFT, vo.getAgentStatus());
        ArgumentCaptor<AgentPublishRequestDO> reqCaptor = ArgumentCaptor.forClass(AgentPublishRequestDO.class);
        verify(requestMapper).updateById(reqCaptor.capture());
        assertEquals("提示词存在合规风险，请修改", reqCaptor.getValue().getReviewComment());
    }

    @Test
    void reject_emptyComment_throws8004() {
        loginAsApprover();
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.reject("req-1", new RejectPublishRequestDTO()));
        assertEquals(8004, e.getCode());
    }

    @Test
    void reject_selfReview_throws8003() {
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());
        RuntimeContext.get().setRole("admin");
        RejectPublishRequestDTO dto = new RejectPublishRequestDTO();
        dto.setComment("x");

        BusinessException e = assertThrows(BusinessException.class, () -> service.reject("req-1", dto));
        assertEquals(8003, e.getCode());
    }

    // ---------------- withdraw ----------------

    @Test
    void withdraw_submitter_success() {
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());
        AgentDO agent = draftAgent();
        agent.setStatus(AgentStatus.PENDING_REVIEW);
        when(agentMapper.selectOne(any())).thenReturn(agent);

        PublishRequestVO vo = service.withdraw("req-1");

        assertEquals("withdrawn", vo.getStatus());
        assertEquals(AgentStatus.DRAFT, vo.getAgentStatus());
    }

    @Test
    void withdraw_notSubmitter_throws8008() {
        loginAsApprover();
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());

        BusinessException e = assertThrows(BusinessException.class, () -> service.withdraw("req-1"));
        assertEquals(8008, e.getCode());
    }
}
