package com.agentone.im.adapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DingTalkSender 签名算法单测。
 * 期望值用 Python hmac/hashlib 独立计算（见 2026-08-26 日志），避免同实现互测
 */
class DingTalkSenderTest {

    @Test
    void sign_knownVector_matchesIndependentImplementation() {
        // python: base64(HmacSHA256("1700000000000\nSEC123456", "SEC123456")) 后 urlencode
        assertEquals("FDly9FmQpdYyYkNLryV5%2F4kGkNb4cCTG2VhnJLEn0mA%3D",
                DingTalkSender.sign(1700000000000L, "SEC123456"));
    }

    @Test
    void sign_differentSecrets_produceDifferentSignatures() {
        assertNotEquals(DingTalkSender.sign(1700000000000L, "secret-a"),
                DingTalkSender.sign(1700000000000L, "secret-b"));
    }

    @Test
    void buildUrl_withSecret_appendsTimestampAndSign() {
        String url = DingTalkSender.buildUrl(
                "https://oapi.dingtalk.com/robot/send?access_token=abc", "SEC123456", 1700000000000L);
        assertTrue(url.startsWith("https://oapi.dingtalk.com/robot/send?access_token=abc&timestamp=1700000000000&sign="));
        assertTrue(url.contains("FDly9FmQpdYyYkNLryV5%2F4kGkNb4cCTG2VhnJLEn0mA%3D"));
    }

    @Test
    void buildUrl_noQueryAndWithSecret_usesQuestionMark() {
        String url = DingTalkSender.buildUrl("https://oapi.dingtalk.com/robot/send", "SEC123456", 1700000000000L);
        assertTrue(url.contains("?timestamp=1700000000000&sign="));
    }

    @Test
    void buildUrl_blankSecret_returnsOriginalUrl() {
        String url = "https://oapi.dingtalk.com/robot/send?access_token=abc";
        assertEquals(url, DingTalkSender.buildUrl(url, null, 1700000000000L));
        assertEquals(url, DingTalkSender.buildUrl(url, "  ", 1700000000000L));
    }
}
