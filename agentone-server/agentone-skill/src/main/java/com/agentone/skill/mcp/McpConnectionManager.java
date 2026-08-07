package com.agentone.skill.mcp;

import com.agentone.skill.entity.McpServerDO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 连接管理器：持有每个 MCP Server 的活跃客户端。
 *
 * 连接是重资源（stdio = 子进程，sse/streamable_http = 长连接），
 * 由本组件统一持有与释放；SkillRegistry 只存无状态的执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpConnectionManager {

    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    /** serverId → 活跃客户端 */
    private final Map<String, McpClientWrapper> clients = new ConcurrentHashMap<>();

    /**
     * 按配置构建客户端并完成协议握手（阻塞，受 timeoutMs 约束）。
     * 同一 server 重复 open 时先关闭旧连接。
     */
    public McpClientWrapper open(McpServerDO server) {
        close(server.getId());

        long timeoutMs = server.getTimeoutMs() != null ? server.getTimeoutMs() : 30000L;
        McpClientBuilder builder = McpClientBuilder.create(server.getName())
                .timeout(Duration.ofMillis(timeoutMs))
                .initializationTimeout(Duration.ofMillis(timeoutMs));

        switch (server.getTransport()) {
            // env 传空 Map 而非 null：SDK 内部 new HashMap<>(env)，null 会 NPE
            case "stdio" -> builder.stdioTransport(server.getCommand(), parseArgs(server.getArgs()), Map.of());
            case "sse" -> {
                builder.sseTransport(server.getUrl());
                applyHeaders(builder, server.getHeaders());
            }
            case "streamable_http" -> {
                builder.streamableHttpTransport(server.getUrl());
                applyHeaders(builder, server.getHeaders());
            }
            default -> throw new IllegalArgumentException("不支持的 transport: " + server.getTransport());
        }

        McpClientWrapper client = builder.buildSync();
        try {
            client.initialize().block(Duration.ofMillis(timeoutMs));
        } catch (Exception e) {
            // 握手失败：释放资源再抛出，避免泄漏子进程/连接
            safeClose(client);
            throw e;
        }
        clients.put(server.getId(), client);
        return client;
    }

    /** 关闭并移除连接（幂等） */
    public void close(String serverId) {
        McpClientWrapper old = clients.remove(serverId);
        safeClose(old);
    }

    public McpClientWrapper get(String serverId) {
        return clients.get(serverId);
    }

    public boolean isConnected(String serverId) {
        McpClientWrapper client = clients.get(serverId);
        return client != null && client.isInitialized();
    }

    @PreDestroy
    public void closeAll() {
        clients.keySet().forEach(this::close);
    }

    private void applyHeaders(McpClientBuilder builder, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return;
        }
        try {
            Map<String, String> headers = objectMapper.readValue(headersJson, MAP_TYPE);
            if (!headers.isEmpty()) {
                builder.headers(headers);
            }
        } catch (Exception e) {
            log.warn("MCP headers 解析失败，已忽略: {}", e.getMessage());
        }
    }

    private List<String> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(argsJson, LIST_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void safeClose(McpClientWrapper client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("关闭 MCP 连接失败: {}", e.getMessage());
        }
    }
}
