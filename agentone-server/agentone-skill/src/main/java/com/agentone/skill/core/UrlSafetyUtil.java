package com.agentone.skill.core;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * SSRF 防护工具。
 * 所有会由用户/LLM 控制目标地址的出站请求（HTTP Skill、API 模式 Skill）必须经过此校验。
 *
 * 仅允许 http/https；解析主机并拒绝环回 / 私网 / 链路本地 / 通配地址，
 * 防止打 127.0.0.1、169.254.169.254（云元数据）等内网地址。
 * 注：存在 DNS 重绑定理论风险（检查与连接时解析结果可能不同），
 * 生产环境建议对解析到的地址直连而非依赖主机名（后续增强）。
 */
public final class UrlSafetyUtil {

    private UrlSafetyUtil() {}

    public static void validate(String url) {
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
}
