package com.agentone.skill.controller;

import com.agentone.common.result.PageResult;
import com.agentone.common.result.Result;
import com.agentone.skill.dto.McpServerDTO;
import com.agentone.skill.service.McpServerService;
import com.agentone.skill.vo.McpServerVO;
import com.agentone.skill.vo.McpToolVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP Server 管理 Controller（课题④）
 */
@RestController
@RequestMapping("/api/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;

    /**
     * 登记 MCP Server（仅落库，连接走 /connect）
     */
    @PostMapping
    public Result<McpServerVO> create(@Valid @RequestBody McpServerDTO dto) {
        return Result.ok(mcpServerService.create(dto));
    }

    /**
     * 更新 MCP Server（连接配置变化时旧连接与工具自动失效）
     */
    @PutMapping("/{serverId}")
    public Result<McpServerVO> update(@PathVariable String serverId,
                                      @Valid @RequestBody McpServerDTO dto) {
        return Result.ok(mcpServerService.update(serverId, dto));
    }

    /**
     * 删除 MCP Server（工具仍被 Agent 绑定时拒绝，5012）
     */
    @DeleteMapping("/{serverId}")
    public Result<Void> delete(@PathVariable String serverId) {
        mcpServerService.delete(serverId);
        return Result.ok();
    }

    /**
     * 分页查询当前工作空间的 MCP Server
     */
    @GetMapping
    public Result<PageResult<McpServerVO>> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(mcpServerService.list(page, size));
    }

    /**
     * 查询单个 MCP Server 详情
     */
    @GetMapping("/{serverId}")
    public Result<McpServerVO> get(@PathVariable String serverId) {
        return Result.ok(mcpServerService.get(serverId));
    }

    /**
     * 连接并发现工具：握手 + tools/list，工具注册为虚拟 Skill 后返回
     */
    @PostMapping("/{serverId}/connect")
    public Result<List<McpToolVO>> connect(@PathVariable String serverId) {
        return Result.ok(mcpServerService.connect(serverId));
    }

    /**
     * 断开连接并注销该 Server 注册的全部工具
     */
    @PostMapping("/{serverId}/disconnect")
    public Result<Void> disconnect(@PathVariable String serverId) {
        mcpServerService.disconnect(serverId);
        return Result.ok();
    }

    /**
     * 该 Server 当前已注册的工具（来自 Registry，不要求在线）
     */
    @GetMapping("/{serverId}/tools")
    public Result<List<McpToolVO>> listTools(@PathVariable String serverId) {
        return Result.ok(mcpServerService.listTools(serverId));
    }
}
