package com.agentone.agent.util;

import com.agentone.common.exception.BusinessException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Base URL SSRF 防护 (Bug7)。
 * 任何以用户可控 baseUrl 为目标的出站请求（模型连通性检测等）发起前必须经过本校验。
 * 校验规则: 仅允许 http/https; 解析后的主机不能是
 * 回环 (127.* / localhost), 站点私有 (10.* / 172.16-31.* / 192.168.*),
 * 链路本地 (169.254.*), 任意本地, 共享地址 (100.64/10) 或元数据主机。
 */
public final class BaseUrlValidator {

    private BaseUrlValidator() {
    }

    /**
     * 校验 baseUrl 合法性。null/空白直接放行（由调用方决定默认行为）。
     */
    public static void validateForSsrf(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (Exception e) {
            throw new BusinessException(6006, "非法的 Base URL: " + baseUrl);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new BusinessException(6006, "仅支持 http/https 协议的 Base URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(6006, "无法解析 Base URL 的主机名: " + baseUrl);
        }
        String lowerHost = host.toLowerCase();
        // 显式拦截本地/元数据相关主机名
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".localhost")
                || lowerHost.endsWith(".local") || lowerHost.contains("metadata")) {
            throw new BusinessException(6006, "禁止访问本地/内网地址: " + host);
        }
        // 解析为 IP 后检查是否为私有/回环/链路本地/保留地址
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                throw new BusinessException(6006, "禁止访问本地/内网地址: " + host);
            }
            // 共享地址 100.64.0.0/10（CGNAT，常作云内网）
            byte[] ip = addr.getAddress();
            if (ip.length == 4) {
                int b0 = ip[0] & 0xFF;
                int b1 = ip[1] & 0xFF;
                if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                    throw new BusinessException(6006, "禁止访问保留地址: " + host);
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (UnknownHostException e) {
            throw new BusinessException(6006, "无法解析 Base URL 的主机名: " + host);
        }
    }
}
