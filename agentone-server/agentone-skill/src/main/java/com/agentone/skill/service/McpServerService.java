package com.agentone.skill.service;

import com.agentone.skill.dto.McpServerDTO;
import com.agentone.skill.vo.McpServerVO;
import com.agentone.skill.vo.McpToolVO;
import com.agentone.common.result.PageResult;

import java.util.List;

/**
 * MCP Server 管理服务（课题④）。
 *
 * 生命周期：create（登记）→ connect（握手 + 发现工具并注册为虚拟 Skill）→
 * Agent 绑定使用 → disconnect / delete（注销工具 + 释放连接）。
 */
public interface McpServerService {

    McpServerVO create(McpServerDTO dto);

    McpServerVO update(String serverId, McpServerDTO dto);

    void delete(String serverId);

    PageResult<McpServerVO> list(Integer page, Integer size);

    McpServerVO get(String serverId);

    /** 连接 MCP Server，发现工具并注册进 SkillRegistry，返回工具列表 */
    List<McpToolVO> connect(String serverId);

    /** 断开连接并注销该 Server 的全部工具 */
    void disconnect(String serverId);

    /** 该 Server 当前已注册的工具（来自 Registry，不要求在线），带发布状态 */
    List<McpToolVO> listTools(String serverId);

    /**
     * 发布治理（Skill 中心 v2）：工具级「发布到广场」开关，默认关闭。
     * 发布后工具才在技能广场可见、才可被 Agent 绑定；撤回后立即下线。
     */
    McpToolVO publishTool(String serverId, String toolName, boolean published);

    /** serverId 是否属于当前工作空间（供 bind 做跨租户校验） */
    boolean ownsServer(String serverId);
}
