package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.core.UrlSafetyUtil;
import com.agentone.skill.entity.SkillDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * API 模式 Skill 执行器。
 *
 * 把用户在 Skill 中心登记的 HTTP API 变成可执行的 SkillExecutor：
 * url / method / headers / timeout 固定存放在 skill.config（创建时校验过 SSRF），
 * LLM 在 function calling 中提供的 params 作为请求载荷——
 * POST/PUT 时序列化为 JSON 请求体，GET/DELETE 时拼接为 query 参数。
 *
 * 非 Spring Bean：DB 中每个 api Skill 对应一个实例，
 * 由 UserSkillBootstrap（启动加载）与 SkillService（增删改时）动态注册/注销。
 */
@Slf4j
public class ApiSkillExecutor implements SkillExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 10_000;
    /** 错误消息中回带的响应体上限：足够定位问题又不撑爆 LLM 上下文 */
    private static final int MAX_ERROR_BODY_CHARS = 500;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SkillDO skill;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ApiSkillExecutor(SkillDO skill, WebClient webClient, ObjectMapper objectMapper) {
        this.skill = skill;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SkillResult execute(SkillInvocation invocation, Context context) {
        long start = System.currentTimeMillis();
        try {
            // 生效配置 = Skill 自身 config 深合并调用侧 configOverride（Agent 绑定级覆盖）
            Map<String, Object> config = effectiveConfig(invocation);

            String url = asString(config.get("url"));
            if (url == null || url.isBlank()) {
                return SkillResult.failure("Skill 配置缺少 url", System.currentTimeMillis() - start);
            }
            // 执行期二次校验：防创建后 DNS 变化 / 配置漂移
            UrlSafetyUtil.validate(url);

            String method = asString(config.get("method"));
            method = (method == null || method.isBlank()) ? "POST" : method.toUpperCase();
            long timeout = resolveTimeout(invocation, config);

            Map<String, Object> params = invocation.getParams() != null
                    ? invocation.getParams() : Map.of();

            // GET/DELETE：params 作为 query 参数；POST/PUT：作为 JSON 请求体
            String targetUrl = url;
            if (("GET".equals(method) || "DELETE".equals(method)) && !params.isEmpty()) {
                UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
                params.forEach((k, v) -> builder.queryParam(k, String.valueOf(v)));
                targetUrl = builder.build().toUriString();
            }

            WebClient.RequestBodySpec spec = webClient
                    .method(HttpMethod.valueOf(method))
                    .uri(targetUrl);

            Object headersObj = config.get("headers");
            if (headersObj instanceof Map<?, ?> headers) {
                headers.forEach((k, v) -> spec.header(String.valueOf(k), String.valueOf(v)));
            }

            if (("POST".equals(method) || "PUT".equals(method)) && !params.isEmpty()) {
                spec.contentType(MediaType.APPLICATION_JSON).bodyValue(params);
            }

            ResponseEntity<String> response = spec
                    .retrieve()
                    // 放行非 2xx：默认 toEntity() 对 4xx/5xx 抛 WebClientResponseException，
                    // 业务错误的状态码与响应体会被吞掉，LLM 看不到真正的失败原因
                    .onStatus(HttpStatusCode::isError, resp -> Mono.empty())
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            Integer status = response != null && response.getStatusCode() != null
                    ? response.getStatusCode().value() : null;
            Map<String, Object> data = new HashMap<>();
            data.put("status", status);
            data.put("body", response != null ? response.getBody() : null);
            data.put("url", url);
            data.put("method", method);

            if (status == null || status >= 400) {
                // 失败也把状态码 + 响应体交给 LLM（不含目标 URL），便于其自行纠正参数
                String message = "外部 API 调用失败: HTTP " + (status != null ? status : "无响应");
                String body = response != null ? response.getBody() : null;
                if (body != null && !body.isBlank()) {
                    message = message + ", 响应: " + truncateBody(body);
                }
                log.warn("API Skill 返回非 2xx: id={}, status={}", skill.getId(), status);
                return SkillResult.builder()
                        .success(false)
                        .data(data)
                        .errorMessage(message)
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }
            return SkillResult.success(data, System.currentTimeMillis() - start);
        } catch (IllegalArgumentException e) {
            // UrlSafetyUtil / 参数类错误：详情只落日志，回给调用方的消息不带目标地址
            log.warn("API Skill 执行失败: id={}, error={}", skill.getId(), e.getMessage());
            return SkillResult.failure("外部 API 调用失败: 目标地址不合法或未通过安全校验",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 同理：异常原文可能含完整 URL / 框架内部细节，只回分类后的结论
            log.error("API Skill 执行失败: id={}, error={}", skill.getId(), e.getMessage(), e);
            return SkillResult.failure("外部 API 调用失败: " + classify(e),
                    System.currentTimeMillis() - start);
        }
    }

    /** Skill 自身 config 与本次调用的 configOverride 深合并（覆盖侧优先） */
    private Map<String, Object> effectiveConfig(SkillInvocation invocation) throws Exception {
        Map<String, Object> config = objectMapper.readValue(nullToEmpty(skill.getConfig()), MAP_TYPE);
        Map<String, Object> override = invocation != null ? invocation.getConfigOverride() : null;
        if (override == null || override.isEmpty()) {
            return config;
        }
        return deepMerge(config, override);
    }

    private static Map<String, Object> deepMerge(Map<String, Object> base,
                                                 Map<String, Object> override) {
        Map<String, Object> merged = new HashMap<>(base);
        override.forEach((key, value) -> {
            Object current = merged.get(key);
            if (current instanceof Map<?, ?> currentMap && value instanceof Map<?, ?> overrideMap) {
                merged.put(key, deepMerge(castMap(currentMap), castMap(overrideMap)));
            } else {
                merged.put(key, value);
            }
        });
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /** 超时优先级：本次调用 timeoutMs > config.timeout > 默认 10s */
    private long resolveTimeout(SkillInvocation invocation, Map<String, Object> config) {
        Long invocationTimeout = invocation != null ? invocation.getTimeoutMs() : null;
        if (invocationTimeout != null && invocationTimeout > 0) {
            return invocationTimeout;
        }
        return asLong(config.get("timeout"), DEFAULT_TIMEOUT_MS);
    }

    /** 异常分类：只暴露调用方能理解的结论，不带 URL、堆栈与框架细节 */
    private String classify(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (e instanceof TimeoutException || root instanceof TimeoutException) {
            return "请求超时";
        }
        if (root instanceof UnknownHostException) {
            return "目标主机无法解析";
        }
        if (root instanceof ConnectException) {
            return "无法建立连接";
        }
        if (e instanceof WebClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        if (root instanceof JsonProcessingException) {
            return "Skill 配置不是合法 JSON";
        }
        return "网络或响应异常";
    }

    private String truncateBody(String body) {
        return body.length() <= MAX_ERROR_BODY_CHARS
                ? body : body.substring(0, MAX_ERROR_BODY_CHARS) + "...[truncated]";
    }

    @Override
    public SkillDescriptor getDescriptor() {
        return SkillDescriptor.builder()
                .id(skill.getId())
                .name(skill.getName())
                .description(skill.getDescription())
                .type("api")
                .version(skill.getVersion())
                .source(skill.getSource())
                .category(skill.getCategory())
                // Registry 跨租户共享，描述符携带归属空间供 test/debug 做越权校验
                .workspaceId(skill.getWorkspaceId())
                .enabled("active".equals(skill.getStatus()))
                .actionType(UserSkillExecutors.isActionType(skill, objectMapper))
                .inputSchema(parseSchema(skill.getInputSchema()))
                .outputSchema(parseSchema(skill.getOutputSchema()))
                .build();
    }

    /** JSON Schema 字符串 → Map；缺失或非法时降级为空 object schema */
    private Map<String, Object> parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Skill Schema 解析失败，使用空 Schema: skillId={}, error={}",
                    skill.getId(), e.getMessage());
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private long asLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "{}";
    }
}
