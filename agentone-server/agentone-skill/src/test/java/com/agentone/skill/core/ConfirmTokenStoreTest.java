package com.agentone.skill.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfirmTokenStore 单测（Skill 中心 v2 两阶段确认）：
 * 令牌与「技能 + 参数摘要」绑定、一次性消费、key 顺序不敏感。
 */
class ConfirmTokenStoreTest {

    private ConfirmTokenStore store;

    @BeforeEach
    void setUp() {
        store = new ConfirmTokenStore(new ObjectMapper());
    }

    @Test
    void issueThenConsume_sameSkillAndParams_passes() {
        String token = store.issue("skill-1", Map.of("city", "北京"));
        assertNotNull(token);
        assertTrue(store.consume(token, "skill-1", Map.of("city", "北京")));
    }

    @Test
    void consume_secondTime_rejected() {
        String token = store.issue("skill-1", Map.of());
        assertTrue(store.consume(token, "skill-1", Map.of()));
        assertFalse(store.consume(token, "skill-1", Map.of()), "令牌必须一次性消费");
    }

    @Test
    void consume_paramsTampered_rejected() {
        String token = store.issue("skill-1", Map.of("amount", 100));
        assertFalse(store.consume(token, "skill-1", Map.of("amount", 99999)),
                "拿到令牌后篡改参数必须拒绝");
    }

    @Test
    void consume_wrongSkill_rejected() {
        String token = store.issue("skill-1", Map.of());
        assertFalse(store.consume(token, "skill-2", Map.of()),
                "令牌不可挪用到其他技能");
    }

    @Test
    void consume_nullOrBlankOrUnknownToken_rejected() {
        assertFalse(store.consume(null, "skill-1", Map.of()));
        assertFalse(store.consume("  ", "skill-1", Map.of()));
        assertFalse(store.consume("no-such-token", "skill-1", Map.of()));
    }

    @Test
    void digest_keyOrderInsensitive() {
        Map<String, Object> a = new HashMap<>(Map.of("a", 1, "b", 2));
        Map<String, Object> b = new HashMap<>(Map.of("b", 2, "a", 1));
        String token = store.issue("skill-1", a);
        assertTrue(store.consume(token, "skill-1", b),
                "key 顺序不同但内容相同应视为同一份参数");
    }
}
