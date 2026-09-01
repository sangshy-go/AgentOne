package com.agentone.apikey.controller;

import com.agentone.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.redis.connection.RedisConnection;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查 Controller
 * 供外部监控系统调用，无需认证
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class HealthCheckController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean allHealthy = true;

        // 数据库检查
        Map<String, Object> dbStatus = checkDatabase();
        result.put("database", dbStatus);
        if (!"UP".equals(dbStatus.get("status"))) {
            allHealthy = false;
        }

        // Redis 检查
        Map<String, Object> redisStatus = checkRedis();
        result.put("redis", redisStatus);
        if (!"UP".equals(redisStatus.get("status"))) {
            allHealthy = false;
        }

        result.put("status", allHealthy ? "UP" : "DOWN");
        return Result.ok(result);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> status = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(3);
            status.put("status", valid ? "UP" : "DOWN");
            status.put("type", "PostgreSQL");
        } catch (Exception e) {
            log.warn("数据库健康检查失败");
            status.put("status", "DOWN");
            // 不向外部暴露连接串/主机等内部细节
            status.put("error", "unavailable");
        }
        return status;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> status = new LinkedHashMap<>();
        // try-with-resources 确保连接关闭，避免连接泄漏
        try (RedisConnection conn = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = conn.ping();
            status.put("status", pong != null ? "UP" : "DOWN");
            status.put("type", "Redis");
        } catch (Exception e) {
            log.warn("Redis 健康检查失败");
            status.put("status", "DOWN");
            status.put("error", "unavailable");
        }
        return status;
    }
}
