package com.agentone.auth.controller;

import com.agentone.auth.dto.LoginDTO;
import com.agentone.auth.dto.RegisterDTO;
import com.agentone.auth.service.AuthService;
import com.agentone.auth.util.JwtUtil;
import com.agentone.auth.util.TokenBlacklist;
import com.agentone.auth.vo.AuthVO;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.result.Result;
import com.agentone.common.service.WorkspaceService;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 Controller
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WorkspaceService workspaceService;
    private final JwtUtil jwtUtil;
    private final TokenBlacklist tokenBlacklist;

    @Value("${agentone.jwt.expire-hours:24}")
    private long expireHours;

    @Value("${agentone.jwt.refresh-expire-hours:168}")
    private long refreshExpireHours;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<AuthVO> register(@Valid @RequestBody RegisterDTO dto,
                                   HttpServletRequest request, HttpServletResponse response) {
        AuthVO vo = authService.register(dto);
        setAuthCookie(request, response, vo.getToken());
        setRefreshCookie(request, response, vo.getRefreshToken());
        // 响应体不携带任何 token（认证由 HttpOnly Cookie 承载）
        vo.setToken(null);
        vo.setRefreshToken(null);
        return Result.ok(vo);
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody LoginDTO dto,
                                HttpServletRequest request, HttpServletResponse response) {
        AuthVO vo = authService.login(dto);
        setAuthCookie(request, response, vo.getToken());
        setRefreshCookie(request, response, vo.getRefreshToken());
        vo.setToken(null);
        vo.setRefreshToken(null);
        return Result.ok(vo);
    }

    /**
     * 切换工作空间（会重新签发 JWT 并刷新 Cookie）
     */
    @PostMapping("/switch-workspace/{workspaceId}")
    public Result<AuthVO> switchWorkspace(@PathVariable String workspaceId,
                                          HttpServletRequest request, HttpServletResponse response) {
        String userId = RuntimeContext.getUserId();
        AuthVO vo = authService.switchWorkspace(userId, workspaceId);
        setAuthCookie(request, response, vo.getToken());
        setRefreshCookie(request, response, vo.getRefreshToken());
        vo.setToken(null);
        vo.setRefreshToken(null);
        return Result.ok(vo);
    }

    /**
     * 获取当前登录用户信息（S7：用于无 localStorage token 时的会话恢复）
     */
    @GetMapping("/me")
    public Result<AuthVO> me() {
        String userId = RuntimeContext.getUserId();
        String workspaceId = RuntimeContext.getWorkspaceId();
        // P2: 二次校验账号状态与 workspace 归属（access token 24h 内账号被禁用/移出仍可被发现）
        authService.checkSession(userId, workspaceId);
        AuthVO vo = new AuthVO();
        vo.setUserId(userId);
        vo.setWorkspaceId(workspaceId);
        vo.setWorkspaceName(workspaceService.getWorkspaceName(workspaceId));
        // token 不通过响应体下发，认证由 HttpOnly Cookie 承载
        vo.setToken(null);
        return Result.ok(vo);
    }

    /**
     * 退出登录：清除 HttpOnly Cookie（access + refresh）
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 服务端失效：将当前 access / refresh 的 jti 加入黑名单，登出后旧 token 立即作废
        blacklistToken(extractCookie(request, "agentone_token"));
        blacklistToken(extractCookie(request, "agentone_refresh"));
        clearAuthCookie(request, response, "agentone_token");
        clearAuthCookie(request, response, "agentone_refresh");
        return Result.ok();
    }

    /**
     * C2: 用 refresh token 无感刷新 access token。refresh 由 HttpOnly Cookie 承载。
     */
    @PostMapping("/refresh")
    public Result<AuthVO> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "agentone_refresh");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.fail(401, "缺少 refresh token，请重新登录");
        }
        try {
            DecodedJWT jwt = jwtUtil.parseToken(refreshToken);
            if (!jwtUtil.isRefreshToken(jwt)) {
                return Result.fail(401, "无效的 refresh token");
            }
            AuthVO vo = authService.refreshToken(
                    jwtUtil.getUserId(jwt),
                    jwtUtil.getWorkspaceId(jwt),
                    jwtUtil.getEmail(jwt));
            // 滚动失效：旧 refresh token 的 jti 立即加入黑名单，防止重放
            blacklistToken(refreshToken);
            setAuthCookie(request, response, vo.getToken());
            setRefreshCookie(request, response, vo.getRefreshToken());
            vo.setToken(null);
            vo.setRefreshToken(null);
            return Result.ok(vo);
        } catch (Exception e) {
            // refresh 失败（过期/伪造）→ 清 cookie 引导重新登录
            clearAuthCookie(request, response, "agentone_token");
            clearAuthCookie(request, response, "agentone_refresh");
            return Result.fail(401, "refresh token 已失效，请重新登录");
        }
    }

    /**
     * S7: 种下 HttpOnly + SameSite Cookie 承载 JWT，避免 token 落入 localStorage 被 XSS 窃取。
     * Secure 仅在 HTTPS（request.isSecure()）时开启，本地 HTTP 开发不受影响。
     */
    private void setAuthCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("agentone_token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setAttribute("SameSite", "Lax");
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge((int) (expireHours * 3600));
        response.addCookie(cookie);
    }

    /** C2: 种下 refresh token 的 HttpOnly Cookie */
    private void setRefreshCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("agentone_refresh", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setAttribute("SameSite", "Lax");
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge((int) (refreshExpireHours * 3600));
        response.addCookie(cookie);
    }

    /** 清除指定名称的 HttpOnly Cookie（登出/刷新失败时使用） */
    private void clearAuthCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if (name.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    /** 将给定 token 的 jti 加入黑名单（解析失败时静默跳过） */
    private void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            DecodedJWT jwt = jwtUtil.parseToken(token);
            tokenBlacklist.blacklist(jwtUtil.getJti(jwt), jwtUtil.getExpiresAt(jwt));
        } catch (Exception ignored) {
            // 解析失败（过期/伪造）无需加入黑名单
        }
    }
}
