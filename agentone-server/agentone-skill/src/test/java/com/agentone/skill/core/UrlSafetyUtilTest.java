package com.agentone.skill.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UrlSafetyUtil（SSRF 防护）单测。
 * 全部使用数字 IP，不依赖 DNS 解析，离线可跑。
 */
class UrlSafetyUtilTest {

    @Test
    void validate_nullOrBlankUrl_throws() {
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate(null));
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate(""));
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate("   "));
    }

    @Test
    void validate_invalidSyntax_throws() {
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate("not a url"));
    }

    @Test
    void validate_nonHttpScheme_throws() {
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate("ftp://9.9.9.9/file"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate("file:///etc/passwd"));
    }

    @Test
    void validate_loopback_throws() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> UrlSafetyUtil.validate("http://127.0.0.1/admin"));
        assertTrue(e.getMessage().contains("内网") || e.getMessage().contains("保留地址"));
    }

    @Test
    void validate_siteLocal_throws() {
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate("http://10.0.0.1/api"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate("http://192.168.1.1/api"));
    }

    @Test
    void validate_linkLocal_throws() {
        // 169.254.169.254 是典型云元数据地址，必须拦截
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate("http://169.254.169.254/latest"));
    }

    @Test
    void validate_missingHost_throws() {
        assertThrows(IllegalArgumentException.class, () -> UrlSafetyUtil.validate("http:///path"));
    }

    @Test
    void validate_publicIp_passes() {
        // 9.9.9.9 为公网数字 IP，无需 DNS；非环回/私网/链路本地
        assertDoesNotThrow(() -> UrlSafetyUtil.validate("https://9.9.9.9/api"));
    }
}
