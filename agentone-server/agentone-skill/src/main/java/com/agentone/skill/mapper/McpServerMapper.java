package com.agentone.skill.mapper;

import com.agentone.skill.entity.McpServerDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MCP Server Mapper（课题④）
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServerDO> {

    /**
     * 启动时加载全部工作空间的 active MCP Server（此时无请求上下文，无法走租户过滤）。
     * 仅供 McpServerBootstrap 调用；请求链路中的查询一律走 BaseMapper（租户拦截生效）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM mcp_server WHERE status = 'active'")
    List<McpServerDO> selectAllActiveMcpServers();
}
