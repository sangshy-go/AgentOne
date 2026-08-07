package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.core.UrlSafetyUtil;
import com.agentone.skill.entity.SkillDO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * API 模式 Skill 执行器。
 *
 * 把用户在 Skill 中心登记的 HTTP API 变成可执行的 SkillExecutor：
 * url / method / headers / timeout 固定存放在 skill.config（创建时校验过 SSRF），
 * LLM 在 function calling 中提供的 params 作为请求载荷——
 * POST/PUT 时序列化为 JSON 请求体，GET/DELETE 时拼接为 query 参数。
 *
 * 非 Spring Bean：DB 中每个 api Skill 对应一个实例，
 * 由 ApiSkillBootstrap（启动加载）与 SkillService（增删改时）动态注册/注销。
 */
@Slf4j
public class ApiSkillExecutor implements SkillExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 10_000;
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
            Map<String, Object> config = objectMapper.readValue(
                    nullToEmpty(skill.getConfig()), MAP_TYPE);

            String url = asString(config.get("url"));
            if (url == null || url.isBlank()) {
                return SkillResult.failure("Skill 配置缺少 url", System.currentTimeMillis() - start);
            }
            // 执行期二次校验：防创建后 DNS 变化 / 配置漂移
            UrlSafetyUtil.validate(url);

            String method = asString(config.get("method"));
            method = (method == null || method.isBlank()) ? "POST" : method.toUpperCase();
            long timeout = asLong(config.get("timeout"), DEFAULT_TIMEOUT_MS);

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
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            Map<String, Object> data = new HashMap<>();
            data.put("status", response.getStatusCode().value());
            data.put("body", response.getBody());
            data.put("url", url);
            data.put("method", method);
            return SkillResult.success(data, System.currentTimeMillis() - start);
        } catch (IllegalArgumentException e) {
            // UrlSafetyUtil / JSON 解析等参数类错误
            log.warn("API Skill 执行失败: id={}, error={}", skill.getId(), e.getMessage());
            return SkillResult.failure(e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("API Skill 执行失败: id={}, error={}", skill.getId(), e.getMessage());
            return SkillResult.failure("API Skill 调用失败: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
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
                .enabled("active".equals(skill.getStatus()))
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
