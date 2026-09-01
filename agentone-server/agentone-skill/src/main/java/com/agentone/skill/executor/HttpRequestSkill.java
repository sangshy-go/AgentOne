package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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
            // 超时优先级：本次调用 timeoutMs > params.timeout > 默认 10s
            Long timeout = invocation.getTimeoutMs() != null && invocation.getTimeoutMs() > 0
                    ? invocation.getTimeoutMs()
                    : getLongParam(params, "timeout", DEFAULT_TIMEOUT_MS);

            if (url == null || url.isBlank()) {
                return SkillResult.failure("URL 不能为空", System.currentTimeMillis() - startTime);
            }

            // S5: SSRF 防护——校验协议与主机，禁止访问内网 / 链路本地地址
            UrlSafetyUtil.validate(url);

            // 构建请求
            WebClient.RequestBodySpec requestSpec = webClient
                    .method(HttpMethod.valueOf(method))
                    .uri(url);

            // 添加请求头：LLM 可能给出数字/布尔等非字符串值，
            // 直接强转 Map<String,String> 会 ClassCastException，统一按字符串归一化
            Object headersObj = params.get("headers");
            if (headersObj instanceof Map<?, ?> headers) {
                headers.forEach((name, value) -> {
                    if (name != null && value != null) {
                        requestSpec.header(String.valueOf(name), String.valueOf(value));
                    }
                });
            }

            // 读取请求体：schema 已声明 body 参数，此前执行时从未附加，导致 POST 永远为空 body。
            // 仅对支持 body 的方法（POST/PUT/PATCH/DELETE）附加，GET 不带 body。
            Object body = params.get("body");
            boolean hasBody = body != null
                    && !(body instanceof Map && ((Map<?, ?>) body).isEmpty());
            boolean methodSupportsBody = method.equals("POST") || method.equals("PUT")
                    || method.equals("PATCH") || method.equals("DELETE");

            WebClient.RequestHeadersSpec<?> finalSpec = requestSpec;
            if (hasBody && methodSupportsBody) {
                // bodyValue 对 Map/对象默认序列化为 application/json（若 headers 已指定 Content-Type 则尊重之）
                finalSpec = requestSpec.bodyValue(body);
            }

            // 发送请求并捕获真实状态码（非硬编码 200）
            org.springframework.http.ResponseEntity<String> responseEntity = finalSpec
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
                .category(SkillCategories.IT)
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
