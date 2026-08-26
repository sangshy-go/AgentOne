package com.agentone.auth.filter;

import com.agentone.auth.util.JwtUtil;
import com.agentone.auth.util.TokenBlacklist;
import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.auth0.jwt.interfaces.DecodedJWT;
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

/**
 * JWT 认证过滤器
 * 从 Authorization Header 解析 JWT，注入 RuntimeContext
 * @Order(1)：必须先于 WorkspaceRbacFilter（@Order(2)，依赖本过滤器注入的 Context）
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklist tokenBlacklist;
    private final ObjectMapper objectMapper;

    /**
     * 不需要认证的路径（白名单）
     */
    private static final String[] WHITE_LIST = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/actuator/health",
            "/v1/",
            "/error"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 白名单放行
        for (String white : WHITE_LIST) {
            if (path.startsWith(white)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 提取 JWT：优先 Authorization 头（API 客户端），否则回退到 HttpOnly Cookie（S7，浏览器 SPA）
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            writeUnauthorized(response, "Missing or invalid Authorization header / cookie");
            return;
        }
        try {
            DecodedJWT jwt = jwtUtil.parseToken(token);

            // 安全防护：拒绝 refresh token 用于普通 API 认证
            // refresh token 有效期 7 天，若可当 access token 用则等于变相绕过 24h 过期限制
            if (jwtUtil.isRefreshToken(jwt)) {
                writeUnauthorized(response, "Refresh token cannot be used for API authentication");
                return;
            }

            // 服务端失效：被注销/滚动失效的 jti 一律拒绝（登出或 refresh 轮换后旧 token 立即失效）
            if (tokenBlacklist.isBlacklisted(jwtUtil.getJti(jwt))) {
                writeUnauthorized(response, "Token has been revoked");
                return;
            }

            String userId = jwtUtil.getUserId(jwt);
            String workspaceId = jwtUtil.getWorkspaceId(jwt);
            String email = jwtUtil.getEmail(jwt);

            // 注入运行时上下文
            Context ctx = Context.of(userId, workspaceId);
            ctx.setEmail(email);
            RuntimeContext.set(ctx);

            try {
                filterChain.doFilter(request, response);
            } finally {
                // 请求结束后清除 ThreadLocal，防止内存泄漏
                RuntimeContext.clear();
            }
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            writeUnauthorized(response, "Invalid or expired token");
        }
    }

    /**
     * 从 Authorization 头或 HttpOnly Cookie 中提取 JWT（S7）
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        // RFC 7235：scheme 大小写不敏感，兼容客户端小写 "bearer "
        if (authHeader != null && authHeader.length() > 7
                && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7);
        }
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if ("agentone_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", 401, "message", message, "data", "")
        ));
    }
}
