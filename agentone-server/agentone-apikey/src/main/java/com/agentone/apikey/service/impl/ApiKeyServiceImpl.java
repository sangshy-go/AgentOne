package com.agentone.apikey.service.impl;

import com.agentone.apikey.dto.CreateApiKeyDTO;
import com.agentone.apikey.entity.ApiKeyDO;
import com.agentone.apikey.mapper.ApiKeyMapper;
import com.agentone.apikey.service.ApiKeyService;
import com.agentone.apikey.vo.ApiKeyVO;
import com.agentone.apikey.vo.CreateApiKeyVO;
import com.agentone.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentone.common.result.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API Key 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "abx_sk_live_";
    private static final String REDIS_USAGE_PREFIX = "apikey:usage:";

    @Override
    public CreateApiKeyVO create(String workspaceId, CreateApiKeyDTO dto) {
        // 生成明文 Key
        String rawKey = generateRawKey();
        String keyHash = sha256(rawKey);
        String keyPrefix = rawKey.substring(0, Math.min(12, rawKey.length()));

        ApiKeyDO entity = new ApiKeyDO();
        entity.setWorkspaceId(workspaceId);
        entity.setKeyHash(keyHash);
        entity.setKeyPrefix(keyPrefix);
        entity.setEnv(dto.getEnv());
        entity.setStatus("active");
        entity.setAllowedAgents(serializeAllowedAgents(dto.getAllowedAgents()));
        entity.setDailyLimit(dto.getDailyLimit() != null ? dto.getDailyLimit() : 1000);
        entity.setCreatedAt(LocalDateTime.now());

        apiKeyMapper.insert(entity);

        CreateApiKeyVO vo = new CreateApiKeyVO();
        vo.setId(entity.getId());
        vo.setApiKey(rawKey);
        vo.setKeyPrefix(keyPrefix);
        vo.setEnv(entity.getEnv());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    @Override
    public PageResult<ApiKeyVO> list(String workspaceId, Integer page, Integer size) {
        Page<ApiKeyDO> p = new Page<>(page, size);
        Page<ApiKeyDO> result = apiKeyMapper.selectPage(p,
                new LambdaQueryWrapper<ApiKeyDO>()
                        .eq(ApiKeyDO::getWorkspaceId, workspaceId)
                        .ne(ApiKeyDO::getStatus, "deleted")
                        .orderByDesc(ApiKeyDO::getCreatedAt));
        Page<ApiKeyVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    public void disable(String workspaceId, String id) {
        ApiKeyDO entity = apiKeyMapper.selectOne(
                new LambdaQueryWrapper<ApiKeyDO>()
                        .eq(ApiKeyDO::getId, id)
                        .eq(ApiKeyDO::getWorkspaceId, workspaceId)
        );
        if (entity == null) {
            throw new BusinessException(2001, "API Key 不存在");
        }
        entity.setStatus("disabled");
        apiKeyMapper.updateById(entity);
    }

    @Override
    public ApiKeyDO validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String keyHash = sha256(rawKey);
        ApiKeyDO entity = apiKeyMapper.selectByKeyHash(keyHash);
        // S2: 禁用 / 删除的 Key 一律拒绝，防止 disable() 形同虚设。
        // 过滤器对 null 统一返回 401（Invalid or disabled API Key）。
        if (entity == null || !"active".equals(entity.getStatus())) {
            return null;
        }
        return entity;
    }

    @Override
    public boolean checkDailyLimit(ApiKeyDO apiKey) {
        String redisKey = buildUsageRedisKey(apiKey.getId());
        String countStr = redisTemplate.opsForValue().get(redisKey);
        long count = countStr != null ? Long.parseLong(countStr) : 0;
        return count < apiKey.getDailyLimit();
    }

    @Override
    public void incrementUsage(ApiKeyDO apiKey) {
        String redisKey = buildUsageRedisKey(apiKey.getId());
        Long count = redisTemplate.opsForValue().increment(redisKey);
        // 首次设置过期时间到当天结束
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, java.time.Duration.ofDays(2));
        }
    }

    // ========== 私有方法 ==========

    private String generateRawKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return KEY_PREFIX + encoded;
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    private String buildUsageRedisKey(String apiKeyId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return REDIS_USAGE_PREFIX + apiKeyId + ":" + date;
    }

    private String serializeAllowedAgents(List<String> agents) {
        if (agents == null || agents.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(agents);
        } catch (JsonProcessingException e) {
            throw new BusinessException(5000, "序列化 allowedAgents 失败");
        }
    }

    private List<String> deserializeAllowedAgents(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("反序列化 allowedAgents 失败: {}", json);
            return Collections.emptyList();
        }
    }

    private ApiKeyVO toVO(ApiKeyDO entity) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(entity.getId());
        vo.setKeyPrefix(entity.getKeyPrefix());
        vo.setEnv(entity.getEnv());
        vo.setStatus(entity.getStatus());
        vo.setAllowedAgents(deserializeAllowedAgents(entity.getAllowedAgents()));
        vo.setDailyLimit(entity.getDailyLimit());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
