package com.agentone.auth.filter;

import com.agentone.auth.util.JwtUtil;
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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * JWT 认证过滤器
 * 从 Authorization Header 解析 JWT，注入 RuntimeContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
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
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
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
