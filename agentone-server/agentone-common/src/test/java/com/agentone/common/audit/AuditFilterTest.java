package com.agentone.common.audit;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.mapper.AuditLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditFilter 单测（课题⑩）：
 * 成功写请求落库 / GET 与跳过路径不落 / 业务失败不落 / 审计异常不阻断业务。
 */
@ExtendWith(MockitoExtension.class)
class AuditFilterTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuditFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuditFilter(auditLogMapper, objectMapper);
        Context ctx = Context.of("user-1", "ws-1");
        ctx.setEmail("user1@agentone.local");
        RuntimeContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    private void run(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
        });
    }

    @Test
    void doFilter_writeWithSubAction_recorded() throws Exception {
        run("POST", "/api/agents/abcdef0123456789abcdef0123456789/stop");

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLogDO audit = captor.getValue();
        assertEquals("agents", audit.getResourceType());
        assertEquals("abcdef0123456789abcdef0123456789", audit.getResourceId());
        assertEquals("stop", audit.getAction());
        assertEquals("user-1", audit.getOperatorId());
        assertEquals("ws-1", audit.getWorkspaceId());
        JsonNode detail = objectMapper.readTree(audit.getDetail());
        assertEquals("user1@agentone.local", detail.get("operatorEmail").asText());
        assertEquals("POST", detail.get("method").asText());
    }

    @Test
    void doFilter_postWithoutId_actionCreate() throws Exception {
        run("POST", "/api/agents");

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(auditLogMapper).insert(captor.capture());
        assertEquals("create", captor.getValue().getAction());
        assertNull(captor.getValue().getResourceId());
    }

    @Test
    void doFilter_getRequest_notRecorded() throws Exception {
        run("GET", "/api/agents");
        verify(auditLogMapper, never()).insert(any(AuditLogDO.class));
    }

    @Test
    void doFilter_skipPrefixes_notRecorded() throws Exception {
        // 对话运行时（含 SSE）、认证、OpenAPI、IM 回调均不落审计
        for (String path : new String[]{"/api/chat/stream", "/api/auth/logout",
                "/v1/chat", "/api/im/callback/dingtalk/bot-1", "/api/workspaces"}) {
            run("POST", path);
        }
        verify(auditLogMapper, never()).insert(any(AuditLogDO.class));
    }

    @Test
    void doFilter_businessErrorAttribute_notRecorded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agents");
        request.setAttribute(AuditFilter.ERROR_CODE_ATTR, 3001);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });
        verify(auditLogMapper, never()).insert(any(AuditLogDO.class));
    }

    @Test
    void doFilter_httpErrorStatus_notRecorded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/agents/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> response.setStatus(400));
        verify(auditLogMapper, never()).insert(any(AuditLogDO.class));
    }

    @Test
    void doFilter_noContext_notRecorded() throws Exception {
        RuntimeContext.clear();
        run("POST", "/api/agents");
        verify(auditLogMapper, never()).insert(any(AuditLogDO.class));
    }

    @Test
    void doFilter_mapperThrows_businessNotAffected() throws Exception {
        // best-effort：审计写入失败绝不阻断业务
        when(auditLogMapper.insert(any(AuditLogDO.class))).thenThrow(new RuntimeException("db down"));
        run("POST", "/api/agents");
        verify(auditLogMapper).insert(any(AuditLogDO.class));
    }
}
