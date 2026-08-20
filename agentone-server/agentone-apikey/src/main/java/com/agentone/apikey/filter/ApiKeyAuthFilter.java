package com.agentone.apikey.filter;

import com.agentone.apikey.entity.ApiKeyDO;
import com.agentone.apikey.service.ApiKeyService;
import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * API Key 认证过滤器
 * 处理 /v1/* 外部 API 请求，通过 X-API-Key Header 认证
 * 优先级在 JwtAuthFilter 之后（Order 更大），仅处理 /v1/* 路径
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 仅处理 /v1/ 路径
        if (!path.startsWith("/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // /v1/health 白名单放行
        if (path.equals("/v1/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 提取 API Key
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            writeError(response, 401, "Missing X-API-Key header");
            return;
        }

        // 校验 API Key（validate 内部使用 @InterceptorIgnore 跳过租户过滤）
        ApiKeyDO entity = apiKeyService.validate(apiKey);
        if (entity == null) {
            writeError(response, 401, "Invalid or disabled API Key");
            return;
        }

        // 检查每日调用限制
        if (!apiKeyService.checkDailyLimit(entity)) {
            writeError(response, 429, "Daily API call limit exceeded");
            return;
        }

        // 注入运行时上下文（API Key 无用户身份，用 workspaceId + 固定 userId）
        Context ctx = Context.of("apikey:" + entity.getId(), entity.getWorkspaceId());
        ctx.setEmail("apikey@" + entity.getKeyPrefix());
        RuntimeContext.set(ctx);

        try {
            // 调用计数已在 checkDailyLimit 内原子自增（通过即代表未超限）
            filterChain.doFilter(request, response);
        } finally {
            RuntimeContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // JwtAuthFilter 已经处理非 /v1/ 路径，这里只处理 /v1/
        return !request.getRequestURI().startsWith("/v1/");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", status, "message", message, "data", "")
        ));
    }
}
