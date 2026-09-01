package com.agentone.skill.config;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.skill.entity.McpServerDO;
import com.agentone.skill.mapper.McpServerMapper;
import com.agentone.skill.service.McpServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Server 启动加载器（课题④）。
 *
 * 应用启动时把 DB 中所有 active 的 MCP Server 重连并重新注册其工具，
 * 让重启后 Agent 已绑定的 MCP 工具立即可用，无需人工逐个 connect。
 *
 * 实现要点：
 * - 启动期没有请求上下文，查询走 McpServerMapper.selectAllActiveMcpServers()
 *   （@InterceptorIgnore 显式跳过租户过滤）；
 * - connect 内部走租户过滤的 selectById/updateById，因此循环内以 server 归属
 *   工作空间临时注入 RuntimeContext（userId 标记为 "bootstrap"），结束即清理；
 * - 单个 Server 连接失败（目标不可达/超时）只记 warn，不阻断启动，
 *   用户可在 MCP 管理页手动重连；
 * - 已知权衡：不可达 Server 要消耗 timeoutMs（默认 30s）才失败，
 *   Server 较多时会拖慢启动。MVP 阶段接受，后续可改异步加载。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpServerBootstrap implements ApplicationRunner {

    private final McpServerMapper mcpServerMapper;
    private final McpServerService mcpServerService;

    @Override
    public void run(ApplicationArguments args) {
        List<McpServerDO> servers = mcpServerMapper.selectAllActiveMcpServers();
        int connected = 0;
        for (McpServerDO server : servers) {
            RuntimeContext.set(Context.of("bootstrap", server.getWorkspaceId()));
            try {
                mcpServerService.connect(server.getId());
                connected++;
            } catch (Exception e) {
                // 单个 Server 连接失败不影响其他 Server 与系统启动
                log.warn("MCP Server 启动连接失败，已跳过: id={}, name={}, error={}",
                        server.getId(), server.getName(), e.getMessage());
            } finally {
                RuntimeContext.clear();
            }
        }
        log.info("MCP Server 启动加载完成: DB 中 {} 个 active，成功连接 {} 个",
                servers.size(), connected);
    }
}
