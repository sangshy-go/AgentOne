package com.agentone.apikey.service.impl;

import com.agentone.apikey.dto.CreateApiKeyDTO;
import com.agentone.apikey.entity.ApiKeyDO;
import com.agentone.apikey.mapper.ApiKeyMapper;
import com.agentone.apikey.service.ApiKeyService;
import com.agentone.apikey.vo.ApiKeyVO;
import com.agentone.apikey.vo.CreateApiKeyVO;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.ResultCode;
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
import java.time.Duration;
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
        // 展示前缀取随机段（跳过固定 "abx_sk_live_" 前缀），避免所有 Key 前缀雷同无标识意义
        String keyPrefix = rawKey.substring(KEY_PREFIX.length(),
                Math.min(KEY_PREFIX.length() + 8, rawKey.length()));

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
        // 分页参数防越界：page 从 1 起，size 限制在 [1,100]，避免 size=-1 在 PG 中 LIMIT 不限导致全表拉取
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? 20 : Math.min(size, 100);
        Page<ApiKeyDO> p = new Page<>(safePage, safeSize);
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
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "API Key 不存在");
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
        // 原子自增 + 限额判定，避免 check-then-act 在并发下被突破（高并发超额 1~2 次）
        long limit = apiKey.getDailyLimit() != null ? apiKey.getDailyLimit() : 1000;
        String redisKey = buildUsageRedisKey(apiKey.getId());
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            // Redis 不可用时失败开放，由调用方正常放行（不阻断业务）
            return true;
        }
        if (count == 1L) {
            // 首次计数：过期时间对齐到当天结束（而非 2 天，避免跨天残留）
            long secondsToMidnight = java.time.Duration.between(
                    java.time.LocalDateTime.now(),
                    java.time.LocalDate.now().plusDays(1).atStartOfDay()).getSeconds();
            redisTemplate.expire(redisKey, Duration.ofSeconds(secondsToMidnight));
        }
        if (count > limit) {
            // 超限请求不计入使用量，回退本次自增，避免误消耗配额
            redisTemplate.opsForValue().decrement(redisKey);
            return false;
        }
        return true;
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
