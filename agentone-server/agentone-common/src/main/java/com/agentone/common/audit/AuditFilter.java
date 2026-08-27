package com.agentone.common.audit;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.mapper.AuditLogMapper;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 变更审计过滤器（课题⑩）：所有成功的写请求统一落 audit_log。
 *
 * 顺序 @Order(3)：在 JwtAuthFilter(1) 与 WorkspaceRbacFilter(2) 之内层，
 * 保证 RuntimeContext（userId/workspaceId/email/role）已就绪；
 * 被前两层拦截的请求不会进入本过滤器（边界拒绝已有各自日志，不重复记录）。
 *
 * 设计决策：
 * 1. 只记成功写请求——业务异常由 GlobalExceptionHandler 设置
 *    {@link #ERROR_CODE_ATTR} 请求属性，本过滤器据此排除（不包
 *    ResponseWrapper 解析响应体，避免挂住 SSE 流）；
 * 2. 不记录请求体——模型 API Key / IM 凭证等密钥走写请求体，落库即泄密；
 * 3. best-effort——审计写入失败只 warn，绝不阻断业务。
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class AuditFilter extends OncePerRequestFilter {

    /** GlobalExceptionHandler 转业务异常时写入的错误码属性；存在即业务失败，不记审计 */
    public static final String ERROR_CODE_ATTR = "agentone.error.code";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    /** UUID（36）或 MyBatis-Plus ASSIGN_UUID 的 32 位 hex */
    private static final Pattern ID_PATTERN = Pattern.compile("[0-9a-fA-F-]{32,36}");

    /**
     * 不落审计的路径：
     * /api/auth/ 登录注册（高频、无工作空间语义）；/api/workspaces 空间切换；
     * /v1/ OpenAPI 运行时调用（chat_api_log 已全量留痕）；
     * /api/chat 对话运行时（chat_message/chat_api_log 已全量留痕，且 /api/chat/stream 为 SSE）；
     * /api/im/callback/ 平台回调（虚拟用户，防刷屏）
     */
    private static final String[] SKIP_PREFIXES = {
            "/api/auth/", "/api/workspaces", "/v1/", "/api/chat",
            "/api/im/callback/", "/actuator", "/error"
    };

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        // best-effort：审计失败不阻断业务响应
        try {
            recordIfWrite(request, response);
        } catch (Exception e) {
            log.warn("审计日志写入失败（已忽略，不影响业务）: {}", e.getMessage());
        }
    }

    private void recordIfWrite(HttpServletRequest request, HttpServletResponse response) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return;
        }
        String path = request.getRequestURI();
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return;
            }
        }
        // HTTP 层失败（校验 400 / 未知异常 500 等）不记
        if (response.getStatus() >= 400) {
            return;
        }
        // 业务异常（HTTP 200 + code!=0）不记
        if (request.getAttribute(ERROR_CODE_ATTR) != null) {
            return;
        }
        Context ctx = RuntimeContext.get();
        if (ctx == null || ctx.getWorkspaceId() == null || ctx.getUserId() == null) {
            return;
        }

        AuditLogDO audit = new AuditLogDO();
        audit.setWorkspaceId(ctx.getWorkspaceId());
        audit.setOperatorId(ctx.getUserId());
        fillResourceInfo(audit, request.getMethod(), path);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("method", request.getMethod());
        detail.put("path", path);
        if (ctx.getEmail() != null) {
            detail.put("operatorEmail", ctx.getEmail());
        }
        try {
            audit.setDetail(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            audit.setDetail("{}");
        }
        audit.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(audit);
    }

    /**
     * 从路径推导资源信息：/api/{resource}[/{id}][/{sub}]
     * resource_type = 首段；第二段形如 UUID 则取 resource_id，其后一段为语义动作；
     * 无子路径时按 HTTP 方法兜底（create/update/delete）；解析不出不硬猜
     */
    private void fillResourceInfo(AuditLogDO audit, String method, String path) {
        String rel = path.startsWith("/api/") ? path.substring("/api/".length()) : path;
        String[] segs = rel.split("/");
        audit.setResourceType(segs[0]);
        String action = null;
        if (segs.length >= 2 && ID_PATTERN.matcher(segs[1]).matches()) {
            audit.setResourceId(segs[1]);
            if (segs.length >= 3) {
                action = segs[2];
            }
        } else if (segs.length >= 2) {
            action = segs[1];
        }
        if (action == null) {
            action = switch (method) {
                case "POST" -> "create";
                case "PUT", "PATCH" -> "update";
                case "DELETE" -> "delete";
                default -> "write";
            };
        }
        audit.setAction(action);
    }
}
