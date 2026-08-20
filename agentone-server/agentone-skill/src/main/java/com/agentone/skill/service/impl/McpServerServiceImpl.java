package com.agentone.skill.service.impl;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.PageResult;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.dto.McpServerDTO;
import com.agentone.skill.entity.AgentSkillBindingDO;
import com.agentone.skill.entity.McpServerDO;
import com.agentone.skill.entity.McpToolPublishDO;
import com.agentone.skill.executor.McpSkillExecutor;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.McpServerMapper;
import com.agentone.skill.mapper.McpToolPublishMapper;
import com.agentone.skill.mcp.McpConnectionManager;
import com.agentone.skill.service.McpServerService;
import com.agentone.skill.vo.McpServerVO;
import com.agentone.skill.vo.McpToolVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP Server 管理实现（课题④）。
 *
 * 工具注册策略（与 builtin 虚拟挂载一致）：发现的 MCP 工具不落 skill 表，
 * 以 "mcp-{serverId}-{toolName}" 虚拟 Skill 注册进 SkillRegistry；
 * MCP 描述符携带 workspaceId，列表合并/绑定/调试据此做租户过滤。
 *
 * 安全边界说明：
 * - MCP 的目标地址/命令由工作空间管理员配置（与 Dify/n8n 等同类产品一致），
 *   stdio 传输本身就是"执行本地命令"的能力，故 url/command 不做 UrlSafetyUtil
 *   的 SSRF 拦截——内网 MCP Server 是合法目标。该信任边界已写入技术方案文档。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private static final Set<String> TRANSPORTS = Set.of("stdio", "sse", "streamable_http");
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final McpServerMapper mcpServerMapper;
    private final AgentSkillBindingMapper bindingMapper;
    private final SkillRegistry skillRegistry;
    private final McpConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final McpToolPublishMapper mcpToolPublishMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerVO create(McpServerDTO dto) {
        validate(dto);

        McpServerDO server = new McpServerDO();
        server.setWorkspaceId(RuntimeContext.getWorkspaceId());
        applyDto(server, dto);
        if (server.getStatus() == null || server.getStatus().isBlank()) {
            server.setStatus("active");
        }
        if (server.getTimeoutMs() == null) {
            server.setTimeoutMs(30000);
        }
        server.setCreatedAt(LocalDateTime.now());
        server.setUpdatedAt(LocalDateTime.now());
        mcpServerMapper.insert(server);
        return toVO(server);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerVO update(String serverId, McpServerDTO dto) {
        McpServerDO server = requireServer(serverId);
        validate(dto);

        // 连接配置变化（含 headers / args / timeoutMs）时，旧连接与其注册的工具全部失效，需重新 connect。
        // 结构化比较，避免 JSON 序列化顺序差异导致误判；args/headers 为空时归一为默认空集合。
        List<?> oldArgs = readJson(server.getArgs(), LIST_TYPE, List.of());
        List<?> newArgs = dto.getArgs() != null ? dto.getArgs() : List.of();
        Map<String, String> oldHeaders = readJson(server.getHeaders(), MAP_TYPE, Map.of());
        Map<String, String> newHeaders = dto.getHeaders() != null ? dto.getHeaders() : Map.of();
        boolean connectionDirty = !java.util.Objects.equals(server.getTransport(), dto.getTransport())
                || !java.util.Objects.equals(server.getUrl(), dto.getUrl())
                || !java.util.Objects.equals(server.getCommand(), dto.getCommand())
                || !java.util.Objects.equals(oldArgs, newArgs)
                || !java.util.Objects.equals(oldHeaders, newHeaders)
                || (dto.getTimeoutMs() != null
                    && !java.util.Objects.equals(server.getTimeoutMs(), dto.getTimeoutMs()));

        // status 转为 disabled 时无论连接参数是否变化都必须关闭连接并注销工具，否则 disabled 的 Server 仍在线。
        boolean toDisabled = "disabled".equals(dto.getStatus());

        if ((connectionDirty || toDisabled) && connectionManager.isConnected(serverId)) {
            unregisterServerTools(serverId);
            connectionManager.close(serverId);
        }

        applyDto(server, dto);
        server.setUpdatedAt(LocalDateTime.now());
        mcpServerMapper.updateById(server);
        return toVO(server);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String serverId) {
        requireServer(serverId);

        // 删除保护：该 Server 注册的 MCP 工具仍被 Agent 绑定时拒绝
        Long bindingCount = bindingMapper.selectCount(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .likeRight(AgentSkillBindingDO::getSkillId, McpSkillExecutor.SKILL_ID_PREFIX + serverId));
        if (bindingCount > 0) {
            throw new BusinessException(5012, "该 MCP Server 的工具仍被 Agent 绑定，请先解除绑定");
        }

        unregisterServerTools(serverId);
        connectionManager.close(serverId);
        // 级联清理发布治理状态（Server 删除后其工具发布记录无意义）
        mcpToolPublishMapper.delete(new LambdaQueryWrapper<McpToolPublishDO>()
                .eq(McpToolPublishDO::getServerId, serverId));
        mcpServerMapper.deleteById(serverId);
    }

    @Override
    public PageResult<McpServerVO> list(Integer page, Integer size) {
        Page<McpServerDO> p = new Page<>(page, size);
        Page<McpServerDO> result = mcpServerMapper.selectPage(p,
                new LambdaQueryWrapper<McpServerDO>().orderByDesc(McpServerDO::getCreatedAt));
        List<McpServerVO> records = result.getRecords().stream().map(this::toVO).toList();
        Page<McpServerVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return PageResult.of(voPage);
    }

    @Override
    public McpServerVO get(String serverId) {
        return toVO(requireServer(serverId));
    }

    @Override
    public List<McpToolVO> connect(String serverId) {
        McpServerDO server = requireServer(serverId);

        McpClientWrapper client;
        try {
            client = connectionManager.open(server);
        } catch (Exception e) {
            log.warn("MCP Server 连接失败: id={}, name={}, error={}", serverId, server.getName(), e.getMessage());
            throw new BusinessException(5011, "MCP Server 连接失败: " + rootMessage(e));
        }

        try {
            long timeoutMs = server.getTimeoutMs() != null ? server.getTimeoutMs() : 30000L;
            List<McpSchema.Tool> tools = client.listTools().block(Duration.ofMillis(timeoutMs));
            if (tools == null) {
                tools = List.of();
            }

            // 先注销旧工具再注册，避免重连后残留
            unregisterServerTools(serverId);
            for (McpSchema.Tool tool : tools) {
                skillRegistry.register(new McpSkillExecutor(client, tool, serverId, server.getName(),
                        server.getWorkspaceId(), timeoutMs));
            }

            server.setLastConnectedAt(LocalDateTime.now());
            mcpServerMapper.updateById(server);
            log.info("MCP Server 连接成功: id={}, name={}, 发现工具 {} 个", serverId, server.getName(), tools.size());
            Set<String> publishedNames = publishedToolNames(serverId);
            return tools.stream().map(t -> toToolVO(serverId, t, publishedNames.contains(t.name()))).toList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 工具发现/注册失败：best-effort 清理上一轮可能残留的虚拟工具注册
            // （其 client 已被打开或即将关闭，残留执行器会导致 Agent 调用失败）。
            // open() 已关闭旧 client，这里再注销旧工具，最后关闭新 client。
            unregisterServerTools(serverId);
            connectionManager.close(serverId);
            log.warn("MCP Server 工具发现失败: id={}, error={}", serverId, e.getMessage());
            throw new BusinessException(5011, "MCP 工具发现失败: " + rootMessage(e));
        }
    }

    @Override
    public void disconnect(String serverId) {
        requireServer(serverId);
        unregisterServerTools(serverId);
        connectionManager.close(serverId);
    }

    @Override
    public List<McpToolVO> listTools(String serverId) {
        requireServer(serverId);
        String prefix = McpSkillExecutor.SKILL_ID_PREFIX + serverId + "-";
        Set<String> publishedNames = publishedToolNames(serverId);
        return skillRegistry.listDescriptors().stream()
                .filter(d -> d.getId() != null && d.getId().startsWith(prefix))
                .map(d -> {
                    McpToolVO vo = new McpToolVO();
                    vo.setSkillId(d.getId());
                    vo.setToolName(d.getId().substring(prefix.length()));
                    vo.setDescription(d.getDescription());
                    vo.setInputSchema(writeJson(d.getInputSchema()));
                    vo.setPublished(publishedNames.contains(vo.getToolName()));
                    vo.setActionType(d.isActionType());
                    return vo;
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpToolVO publishTool(String serverId, String toolName, boolean published) {
        requireServer(serverId);
        if (toolName == null || toolName.isBlank()) {
            throw new BusinessException(5010, "MCP 工具名不合法");
        }

        // upsert 发布状态：行不存在则插入（默认关闭 → 首次操作即显式意图）
        McpToolPublishDO row = mcpToolPublishMapper.selectOne(
                new LambdaQueryWrapper<McpToolPublishDO>()
                        .eq(McpToolPublishDO::getServerId, serverId)
                        .eq(McpToolPublishDO::getToolName, toolName));
        if (row == null) {
            row = new McpToolPublishDO();
            row.setWorkspaceId(RuntimeContext.getWorkspaceId());
            row.setServerId(serverId);
            row.setToolName(toolName);
            row.setPublished(published);
            row.setUpdatedAt(LocalDateTime.now());
            mcpToolPublishMapper.insert(row);
        } else {
            row.setPublished(published);
            row.setUpdatedAt(LocalDateTime.now());
            mcpToolPublishMapper.updateById(row);
        }
        log.info("MCP 工具发布状态变更: serverId={}, tool={}, published={}", serverId, toolName, published);

        McpToolVO vo = new McpToolVO();
        vo.setSkillId(McpSkillExecutor.skillIdOf(serverId, toolName));
        vo.setToolName(toolName);
        vo.setPublished(published);
        // 工具在线时补充描述与 schema（离线发布仅持久化状态，连接后列表自动带出）
        skillRegistry.getExecutor(vo.getSkillId()).ifPresent(executor -> {
            SkillDescriptor descriptor = executor.getDescriptor();
            vo.setDescription(descriptor.getDescription());
            vo.setInputSchema(writeJson(descriptor.getInputSchema()));
            vo.setActionType(descriptor.isActionType());
        });
        return vo;
    }

    /** 该 Server 已发布的工具名集合（mcp_tool_publish.published=true） */
    private Set<String> publishedToolNames(String serverId) {
        return mcpToolPublishMapper.selectList(
                        new LambdaQueryWrapper<McpToolPublishDO>()
                                .eq(McpToolPublishDO::getServerId, serverId)
                                .eq(McpToolPublishDO::getPublished, true))
                .stream()
                .map(McpToolPublishDO::getToolName)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public boolean ownsServer(String serverId) {
        // selectById 自带租户过滤：其他工作空间的 Server 查不到即视为不属于当前空间
        return mcpServerMapper.selectById(serverId) != null;
    }

    /** 注销某 Server 注册的全部 MCP 工具 */
    private void unregisterServerTools(String serverId) {
        String prefix = McpSkillExecutor.SKILL_ID_PREFIX + serverId + "-";
        skillRegistry.listDescriptors().stream()
                .map(SkillDescriptor::getId)
                .filter(id -> id != null && id.startsWith(prefix))
                .toList()
                .forEach(skillRegistry::unregister);
    }

    private McpServerDO requireServer(String serverId) {
        McpServerDO server = mcpServerMapper.selectById(serverId);
        if (server == null) {
            throw new BusinessException(5009, "MCP Server 不存在");
        }
        return server;
    }

    private void validate(McpServerDTO dto) {
        if (!TRANSPORTS.contains(dto.getTransport())) {
            throw new BusinessException(5010, "transport 必须是 stdio / sse / streamable_http");
        }
        if ("stdio".equals(dto.getTransport())
                && (dto.getCommand() == null || dto.getCommand().isBlank())) {
            throw new BusinessException(5010, "stdio 传输必须提供 command");
        }
        if (("sse".equals(dto.getTransport()) || "streamable_http".equals(dto.getTransport()))
                && (dto.getUrl() == null || dto.getUrl().isBlank())) {
            throw new BusinessException(5010, dto.getTransport() + " 传输必须提供 url");
        }
    }

    private void applyDto(McpServerDO server, McpServerDTO dto) {
        server.setName(dto.getName());
        server.setDescription(dto.getDescription());
        server.setTransport(dto.getTransport());
        server.setUrl(dto.getUrl());
        server.setCommand(dto.getCommand());
        server.setArgs(writeJson(dto.getArgs() != null ? dto.getArgs() : List.of()));
        server.setHeaders(writeJson(dto.getHeaders() != null ? dto.getHeaders() : Map.of()));
        if (dto.getTimeoutMs() != null) {
            server.setTimeoutMs(dto.getTimeoutMs());
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            server.setStatus(dto.getStatus());
        }
    }

    private McpServerVO toVO(McpServerDO server) {
        McpServerVO vo = new McpServerVO();
        BeanUtils.copyProperties(server, vo);
        vo.setArgs(readJson(server.getArgs(), LIST_TYPE, List.of()));
        vo.setHeaders(readJson(server.getHeaders(), MAP_TYPE, Map.of()));
        vo.setConnected(connectionManager.isConnected(server.getId()));
        String prefix = McpSkillExecutor.SKILL_ID_PREFIX + server.getId() + "-";
        vo.setToolCount((int) skillRegistry.listDescriptors().stream()
                .filter(d -> d.getId() != null && d.getId().startsWith(prefix))
                .count());
        return vo;
    }

    private McpToolVO toToolVO(String serverId, McpSchema.Tool tool, boolean published) {
        McpToolVO vo = new McpToolVO();
        vo.setSkillId(McpSkillExecutor.skillIdOf(serverId, tool.name()));
        vo.setToolName(tool.name());
        vo.setDescription(tool.description());
        vo.setInputSchema(writeJson(tool.inputSchema()));
        vo.setPublished(published);
        // MCP 工具默认动作型（SDK 0.9.0 无 annotations，副作用未知保守处理）
        vo.setActionType(true);
        return vo;
    }

    private String rootMessage(Exception e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }
}
