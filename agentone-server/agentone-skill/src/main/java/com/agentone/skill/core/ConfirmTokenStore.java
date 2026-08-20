package com.agentone.skill.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动作型技能执行确认令牌（Skill 中心 v2 治理）。
 *
 * 两阶段执行：动作型技能首次调用返回草稿 + 令牌，用户确认后携带令牌二次调用才真实执行。
 * 令牌与「技能 + 参数摘要」绑定（防止拿到令牌后篡改参数），5 分钟过期，一次性消费。
 * 内存态即可：确认是秒级交互，重启后重新生成草稿无业务损失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmTokenStore {

    /** 确认窗口：业务用户看草稿→点确认的合理时长 */
    private static final long TTL_MS = 5 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    private record Entry(String skillId, String paramsDigest, long expiresAt) {}

    /** 签发令牌（绑定技能与参数摘要） */
    public String issue(String skillId, Map<String, Object> params) {
        evictExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new Entry(skillId, digest(params), System.currentTimeMillis() + TTL_MS));
        return token;
    }

    /**
     * 校验并消费令牌：技能匹配、参数未被篡改、未过期才放行；一次性使用。
     */
    public boolean consume(String token, String skillId, Map<String, Object> params) {
        evictExpired();
        if (token == null || token.isBlank()) {
            return false;
        }
        Entry entry = tokens.remove(token);
        if (entry == null) {
            return false;
        }
        return entry.expiresAt() > System.currentTimeMillis()
                && entry.skillId().equals(skillId)
                && entry.paramsDigest().equals(digest(params));
    }

    /**
     * 参数摘要：递归规范化（各层 Map 按 key 排序 + 数值归一）后序列化再 SHA-256。
     * 只排顶层 key 会让嵌套 Map 的插入顺序、以及 JSON 往返造成的 1 → 1.0 精度变化
     * 改变摘要，导致用户明明没改参数也校验失败（5015）。
     */
    private String digest(Map<String, Object> params) {
        try {
            String json = objectMapper.writeValueAsString(
                    canonicalize(params != null ? params : Map.of()));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("确认令牌参数摘要失败，按空参数处理: {}", e.getMessage());
            return "invalid";
        }
    }

    /**
     * 摘要前规范化：Map 逐层按 key 排序（顺序无关），数值统一为去尾零的十进制字符串
     * （Integer 1 / Long 1 / Double 1.0 视为同一个值）；List 顺序有语义，保持原序。
     */
    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), canonicalize(v)));
            return sorted;
        }
        if (value instanceof Iterable<?> items) {
            List<Object> list = new ArrayList<>();
            items.forEach(item -> list.add(canonicalize(item)));
            return list;
        }
        if (value instanceof Number number) {
            return canonicalizeNumber(number);
        }
        return value;
    }

    /** 数值归一：带类型前缀避免与用户传入的同形字符串混淆；NaN/Infinity 等降级为原文 */
    private String canonicalizeNumber(Number number) {
        try {
            return "num:" + new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return "num:" + number;
        }
    }

    /** 惰性清理过期令牌（签发/消费时触发，无需后台线程） */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }
}
