package com.agentone.auth.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 * 负责生成和解析 Token
 */
@Component
public class JwtUtil {

    @Value("${agentone.jwt.secret}")
    private String secret;

    @Value("${agentone.jwt.expire-hours:24}")
    private int expireHours;

    /** C2: refresh token 有效时长（小时），默认 7 天 */
    @Value("${agentone.jwt.refresh-expire-hours:168}")
    private int refreshExpireHours;

    /**
     * 本地开发逃生开关：设为 true 可临时放行已知的弱默认密钥（禁止用于生产）。
     * 通过环境变量 AGENTONE_ALLOW_INSECURE_JWT=true 开启。
     */
    @Value("${agentone.jwt.allow-insecure-default:false}")
    private boolean allowInsecureDefault;

    /** 历史上写死在配置里的公开弱密钥，任何部署都不应继续使用 */
    private static final String KNOWN_INSECURE_DEFAULT = "agentone-dev-secret-key-2026";

    /**
     * S1: 启动强校验——JWT 密钥缺失、过短或使用已知弱默认密钥时直接拒绝启动，
     * 避免默认 Docker 部署暴露在「可伪造任意用户 token」的越权风险下。
     */
    @PostConstruct
    public void validateSecretOnStartup() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 密钥未配置（agentone.jwt.secret / JWT_SECRET 为空）。"
                            + "生产环境必须在 .env 中设置强随机密钥，例如：JWT_SECRET=$(openssl rand -base64 48)");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT 密钥长度不足 32 位，存在被暴力破解风险，请设置更长的随机密钥。");
        }
        if (KNOWN_INSECURE_DEFAULT.equals(secret) && !allowInsecureDefault) {
            throw new IllegalStateException(
                    "JWT 密钥使用了公开的默认弱密钥（agentone-dev-secret-key-2026），存在完整越权风险。"
                            + "请通过 JWT_SECRET 设置强随机密钥；本地开发可设 AGENTONE_ALLOW_INSECURE_JWT=true 临时放行（禁止用于生产）。");
        }
    }

    /**
     * 生成 JWT Token
     *
     * @param userId      用户 ID
     * @param workspaceId 当前工作空间 ID
     * @param email       邮箱
     * @return JWT 字符串
     */
    public String generateToken(String userId, String workspaceId, String email) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + (long) expireHours * 3600 * 1000);

        return JWT.create()
                .withClaim("user_id", userId)
                .withClaim("workspace_id", workspaceId)
                .withClaim("email", email)
                .withClaim("type", "access")
                .withClaim("jti", UUID.randomUUID().toString())
                .withIssuedAt(now)
                .withExpiresAt(expireAt)
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 生成 Refresh Token（C2：用于无感刷新 access token）。
     * 携带 type=refresh 声明以区分 access token，过期时间更长。
     */
    public String generateRefreshToken(String userId, String workspaceId, String email) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + (long) refreshExpireHours * 3600 * 1000);

        return JWT.create()
                .withClaim("user_id", userId)
                .withClaim("workspace_id", workspaceId)
                .withClaim("email", email)
                .withClaim("type", "refresh")
                .withClaim("jti", UUID.randomUUID().toString())
                .withIssuedAt(now)
                .withExpiresAt(expireAt)
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 判断是否为 refresh token（依据 type 声明）。
     */
    public boolean isRefreshToken(DecodedJWT jwt) {
        return "refresh".equals(jwt.getClaim("type").asString());
    }

    /**
     * 解析 JWT Token
     *
     * @param token JWT 字符串
     * @return 解析结果，失败抛异常
     */
    public DecodedJWT parseToken(String token) {
        return JWT.require(Algorithm.HMAC256(secret))
                .build()
                .verify(token);
    }

    public String getUserId(DecodedJWT jwt) {
        return jwt.getClaim("user_id").asString();
    }

    public String getWorkspaceId(DecodedJWT jwt) {
        return jwt.getClaim("workspace_id").asString();
    }

    public String getEmail(DecodedJWT jwt) {
        return jwt.getClaim("email").asString();
    }

    public String getJti(DecodedJWT jwt) {
        return jwt.getClaim("jti").asString();
    }

    public Date getExpiresAt(DecodedJWT jwt) {
        return jwt.getExpiresAt();
    }
}
