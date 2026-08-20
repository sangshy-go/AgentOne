-- ============================================================
-- V16: MCP Server 管理表（课题④ MCP 集成）
--
-- 每个工作空间可登记若干 MCP Server；连接成功后发现的工具以
-- "mcp-{serverId}-{toolName}" 的虚拟 Skill 形式注册进 SkillRegistry
-- （不落 skill 表，同 builtin 虚拟挂载），可被 Agent 绑定并经
-- function calling 调用，复用既有工具化与审计链路。
-- ============================================================

-- 兜底：清除 V4 遗留的旧结构 mcp_server 表（如有），确保全新/已有环境均能正常迁移
DROP TABLE IF EXISTS mcp_server;

CREATE TABLE mcp_server (
    id                VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id      VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    name              VARCHAR(100) NOT NULL,
    description       TEXT,
    transport         VARCHAR(20)  NOT NULL,               -- stdio / sse / streamable_http
    url               VARCHAR(500),                        -- sse / streamable_http 的目标地址
    command           VARCHAR(500),                        -- stdio 的启动命令
    args              JSONB        NOT NULL DEFAULT '[]',  -- stdio 命令参数
    headers           JSONB        NOT NULL DEFAULT '{}',  -- sse / streamable_http 自定义头
    timeout_ms        INTEGER      NOT NULL DEFAULT 30000, -- 调用超时
    status            VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active / disabled
    last_connected_at TIMESTAMP,                           -- 最近一次连接成功时间
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_mcp_server_workspace ON mcp_server(workspace_id);
CREATE INDEX idx_mcp_server_status    ON mcp_server(status);
