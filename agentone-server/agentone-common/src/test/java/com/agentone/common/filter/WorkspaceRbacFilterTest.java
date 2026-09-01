package com.agentone.common.filter;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.service.WorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkspaceRbacFilter 单测（课题⑥）：
 * 非成员 2002 / observer 写拦截 2004 / 成员管理门禁 2003 / 公共前缀与无上下文放行。
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceRbacFilterTest {

    @Mock
    private WorkspaceService workspaceService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkspaceRbacFilter filter;

    @BeforeEach
    void setUp() {
        filter = new WorkspaceRbacFilter(workspaceService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    /** 执行过滤器，返回 chain 是否被调用 */
    private boolean run(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);
        filter.doFilter(request, response, chain);
        return chainCalled.get();
    }

    private int responseBodyCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(403, response.getStatus());
        return body.get("code").asInt();
    }

    private void loginAs(String role) {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
        when(workspaceService.getUserRole("user-1", "ws-1")).thenReturn(role);
    }

    @Test
    void doFilter_noContext_passesThroughWithoutRoleCheck() throws Exception {
        // 未登录路径（JwtAuthFilter 白名单）：无上下文直接放行，不查角色
        assertTrue(run("GET", "/api/agents"));
        verify(workspaceService, never()).getUserRole("user-1", "ws-1");
    }

    @Test
    void doFilter_publicPrefix_skipsRoleCheck() throws Exception {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
        assertTrue(run("POST", "/api/auth/logout"));
        verify(workspaceService, never()).getUserRole("user-1", "ws-1");
    }

    @Test
    void doFilter_notMember_returns2002() throws Exception {
        loginAs(null);
        assertEquals(2002, responseBodyCode());
    }

    @Test
    void doFilter_observerWrite_returns2004() throws Exception {
        loginAs("observer");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertEquals(403, response.getStatus());
        assertEquals(2004, objectMapper.readTree(response.getContentAsString()).get("code").asInt());
        assertFalse(chainCalled.get(), "observer 的写请求不应到达控制器");
    }

    @Test
    void doFilter_observerRead_passesAndFillsRole() throws Exception {
        loginAs("observer");
        assertTrue(run("GET", "/api/agents"));
        assertEquals("observer", RuntimeContext.getRole(), "角色应写入 Context 供下游使用");
    }

    @Test
    void doFilter_developerWrite_passes() throws Exception {
        loginAs("developer");
        assertTrue(run("POST", "/api/agents"));
    }

    @Test
    void doFilter_auditorWrite_returns2004() throws Exception {
        // 课题⑩：auditor 与 observer 同等只读
        loginAs("auditor");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/publish-requests/req-1/approve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertEquals(403, response.getStatus());
        assertEquals(2004, objectMapper.readTree(response.getContentAsString()).get("code").asInt());
        assertFalse(chainCalled.get(), "auditor 的写请求不应到达控制器");
    }

    @Test
    void doFilter_auditorRead_passes() throws Exception {
        // auditor 可读（审计日志/审批列表的门禁由各 Controller/Service 按角色细化）
        loginAs("auditor");
        assertTrue(run("GET", "/api/audit-logs"));
    }

    @Test
    void doFilter_auditorAccessMembers_returns2003() throws Exception {
        loginAs("auditor");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/members");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });

        assertEquals(403, response.getStatus());
        assertEquals(2003, objectMapper.readTree(response.getContentAsString()).get("code").asInt());
    }

    @Test
    void doFilter_developerAccessMembers_returns2003() throws Exception {
        loginAs("developer");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/members");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });

        assertEquals(403, response.getStatus());
        assertEquals(2003, objectMapper.readTree(response.getContentAsString()).get("code").asInt());
    }

    @Test
    void doFilter_adminAccessMembers_passes() throws Exception {
        loginAs("admin");
        assertTrue(run("GET", "/api/members"));
    }
}
