package com.agentone.auth.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * P1: 服务端 Token 失效（黑名单）。
 * 基于 Redis 存储被注销/滚动失效的 jti，TTL 设为 token 剩余有效期，
 * 因此到达 token 自然过期后黑名单条目自动清理，不会无限增长。
 *
 * 注意：使用 Redis 承载黑名单；若 Redis 不可用，isBlacklisted 失败开放（放行），
 * 由 JWT 自身的过期时间兜底安全性。生产环境应确保 Redis 高可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "jti:blacklist:";

    /**
     * 将指定 jti 加入黑名单，TTL = token 剩余有效期（秒）。
     * 若 token 已过期（TTL<=0）则不写入。
     */
    public void blacklist(String jti, Date expiresAt) {
        if (jti == null || expiresAt == null) {
            return;
        }
        long ttlSeconds = (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(PREFIX + jti, "1", ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入 token 黑名单失败: {}", e.getMessage());
        }
    }

    /**
     * 判断 jti 是否已被拉黑。Redis 异常时失败开放（放行）。
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
        } catch (Exception e) {
            log.warn("查询 token 黑名单失败（失败开放）: {}", e.getMessage());
            return false;
        }
    }
}
