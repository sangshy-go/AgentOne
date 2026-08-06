package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求 Skill
 * 支持 GET / POST 调用外部 API
 *
 * 输入参数:
 * - method: GET / POST（默认 GET）
 * - url: 请求地址
 * - headers: 请求头（可选）
 * - body: 请求体（POST 时可选）
 * - timeout: 超时时间（默认 10 秒）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpRequestSkill implements SkillExecutor {

    private static final String SKILL_ID = "builtin-http-request";
    private static final long DEFAULT_TIMEOUT_MS = 10000;

    private final WebClient webClient;

    @Override
    public SkillResult execute(SkillInvocation invocation, Context context) {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> params = invocation.getParams();
            String method = getStringParam(params, "method", "GET").toUpperCase();
            String url = getStringParam(params, "url", null);
            Long timeout = getLongParam(params, "timeout", DEFAULT_TIMEOUT_MS);

            if (url == null || url.isBlank()) {
                return SkillResult.failure("URL 不能为空", System.currentTimeMillis() - startTime);
            }

            // S5: SSRF 防护——校验协议与主机，禁止访问内网 / 链路本地地址
            validateUrl(url);

            // 构建请求
            WebClient.RequestBodySpec requestSpec = webClient
                    .method(HttpMethod.valueOf(method))
                    .uri(url);

            // 添加请求头
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) params.get("headers");
            if (headers != null) {
                headers.forEach(requestSpec::header);
            }

            // 发送请求并捕获真实状态码（非硬编码 200）
            org.springframework.http.ResponseEntity<String> responseEntity = requestSpec
                    .retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            int statusCode = responseEntity.getStatusCode().value();
            String responseBody = responseEntity.getBody();

            Map<String, Object> data = new HashMap<>();
            data.put("status", statusCode);
            data.put("body", responseBody);
            data.put("url", url);
            data.put("method", method);

            return SkillResult.success(data, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("HTTP 请求失败: {}", e.getMessage());
            return SkillResult.failure("HTTP 请求失败: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public SkillDescriptor getDescriptor() {
        return SkillDescriptor.builder()
                .id(SKILL_ID)
                .name("HTTP 请求")
                .description("调用外部 HTTP API，支持 GET/POST 方法")
                .type("builtin")
                .version("1.0.0")
                .source("agentone")
                .enabled(true)
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "method", Map.of("type", "string", "enum", new String[]{"GET", "POST", "PUT", "DELETE"}),
                                "url", Map.of("type", "string", "description", "请求 URL"),
                                "headers", Map.of("type", "object", "description", "请求头"),
                                "body", Map.of("type", "object", "description", "请求体"),
                                "timeout", Map.of("type", "integer", "description", "超时时间（毫秒）")
                        ),
                        "required", new String[]{"url"}
                ))
                .build();
    }

    /**
     * S5: SSRF 防护
     * 仅允许 http/https；解析主机并拒绝环回 / 私网 / 链路本地 / 通配地址，
     * 防止打 127.0.0.1、169.254.169.254（云元数据）等内网地址。
     * 注：存在 DNS 重绑定理论风险（检查与连接时解析结果可能不同），
     * 生产环境建议对解析到的地址直连而非依赖主机名（后续增强）。
     */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL 格式非法: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("仅支持 http/https 协议，禁止: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少主机名");
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机: " + host);
        }
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            throw new IllegalArgumentException("禁止访问内网 / 保留地址: " + host);
        }
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Long getLongParam(Map<String, Object> params, String key, Long defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
