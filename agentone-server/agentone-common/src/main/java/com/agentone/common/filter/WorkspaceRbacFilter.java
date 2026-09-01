package com.agentone.common.filter;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.service.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 工作空间 RBAC 过滤器（课题⑥）。
 * 必须运行在 JwtAuthFilter 之后（@Order(2)）：从 DB 按请求加载当前用户在工作空间的角色
 * 写入 Context，并强制：
 * 1. 非成员访问工作空间资源 → 2002
 * 2. observer 全局只读：拦截工作空间内一切写操作 → 2004
 * 3. /api/members/** 仅 admin/owner → 2003
 * 角色每请求从 DB 读取：管理员改角色后即时生效，无需重登录。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class WorkspaceRbacFilter extends OncePerRequestFilter {

    private static final String ROLE_ATTR = "agentone.ws.role";

    /** 与工作空间成员身份无关的路径：放行成员/角色检查 */
    private static final String[] PUBLIC_PREFIXES = {
            "/api/auth/",        // 登录态/切换工作空间等，切换时 JWT 空间可能已失效
            "/api/workspaces",   // 列表/创建/变更：控制器内部自行 checkPermission + 角色检查
            "/v1/",              // OpenAPI：走 API Key 鉴权，不经 JWT
            "/actuator",
            "/error"
    };

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Context ctx = RuntimeContext.get();
        // 未认证路径（白名单）无上下文，直接放行；认证由 JwtAuthFilter 负责
        if (ctx == null || ctx.getWorkspaceId() == null || ctx.getWorkspaceId().isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 按请求加载角色（request 属性缓存，防止同请求重复查库）
        String role = (String) request.getAttribute(ROLE_ATTR);
        if (role == null) {
            role = workspaceService.getUserRole(ctx.getUserId(), ctx.getWorkspaceId());
            if (role != null) {
                request.setAttribute(ROLE_ATTR, role);
            }
        }
        ctx.setRole(role);

        if (role == null) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, 2002, "无权访问该工作空间");
            return;
        }
        // observer / auditor 均为只读角色（课题⑩：auditor 额外可查审计日志与审批列表，读操作此处放行）
        if (("observer".equals(role) || "auditor".equals(role)) && WRITE_METHODS.contains(request.getMethod())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, 2004, "当前角色为只读，不允许该操作");
            return;
        }
        if (path.startsWith("/api/members") && !"admin".equals(role) && !"owner".equals(role)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, 2003, "仅管理员或所有者可管理成员");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int httpStatus,
                            int code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", code, "message", message, "data", "")
        ));
    }
}
