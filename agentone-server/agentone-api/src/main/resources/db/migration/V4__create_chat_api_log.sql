-- ============================================================
-- V4: 会话 + 消息 + API Key + 日志 + 定时任务 + MCP
-- ============================================================

-- 会话表
CREATE TABLE chat_session (
    id            VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id  VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    agent_id      VARCHAR(36)  NOT NULL REFERENCES agent(id),
    user_id       VARCHAR(100) NOT NULL,
    title         VARCHAR(200),
    token_count   BIGINT       NOT NULL DEFAULT 0,
    message_count INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_workspace ON chat_session(workspace_id);
CREATE INDEX idx_session_agent     ON chat_session(agent_id);
CREATE INDEX idx_session_user      ON chat_session(user_id);

-- 消息表
CREATE TABLE chat_message (
    id           VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id   VARCHAR(36)  NOT NULL REFERENCES chat_session(id),
    role         VARCHAR(20)  NOT NULL,  -- user / assistant / system / tool
    content      TEXT         NOT NULL DEFAULT '',
    token_count  INT          NOT NULL DEFAULT 0,
    skill_calls  JSONB,
    duration_ms  INT,
    trace_id     VARCHAR(50),
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_session ON chat_message(session_id);

-- API Key 表
CREATE TABLE api_key (
    id              VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    key_hash        VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix      VARCHAR(12)  NOT NULL,
    env             VARCHAR(10)  NOT NULL DEFAULT 'live',  -- live / test
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active / disabled / deleted
    allowed_agents  JSONB        NOT NULL DEFAULT '[]',
    daily_limit     INT          NOT NULL DEFAULT 1000,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_apikey_workspace ON api_key(workspace_id);
CREATE INDEX idx_apikey_hash      ON api_key(key_hash);

-- Skill 调用日志表
CREATE TABLE skill_call_log (
    id             VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id   VARCHAR(36)  NOT NULL,
    agent_id       VARCHAR(36),
    skill_id       VARCHAR(36),
    session_id     VARCHAR(36),
    trace_id       VARCHAR(50),
    input_params   JSONB        NOT NULL DEFAULT '{}',
    output_result  JSONB        NOT NULL DEFAULT '{}',
    duration_ms    INT,
    token_count    INT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL,  -- success / failed / timeout
    error_message  TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_log_workspace ON skill_call_log(workspace_id);
CREATE INDEX idx_skill_log_agent     ON skill_call_log(agent_id);

-- 审计日志表
CREATE TABLE audit_log (
    id            VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id  VARCHAR(36)  NOT NULL,
    operator_id   VARCHAR(36),
    action        VARCHAR(50)  NOT NULL,
    resource_type VARCHAR(50),
    resource_id   VARCHAR(36),
    detail        JSONB        NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_workspace ON audit_log(workspace_id);
CREATE INDEX idx_audit_action    ON audit_log(action);

-- 定时任务表
CREATE TABLE scheduled_task (
    id              VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(20)  NOT NULL,  -- agent_call / skill_call / notification
    cron_expression VARCHAR(100) NOT NULL,
    config          JSONB        NOT NULL DEFAULT '{}',
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active / paused / error
    last_run_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_workspace ON scheduled_task(workspace_id);

-- 任务执行日志表
CREATE TABLE task_execution_log (
    id           VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id      VARCHAR(36)  NOT NULL REFERENCES scheduled_task(id),
    trigger_time TIMESTAMP    NOT NULL DEFAULT now(),
    duration_ms  INT,
    status       VARCHAR(20)  NOT NULL,  -- success / failed / timeout
    input        JSONB        NOT NULL DEFAULT '{}',
    output       JSONB        NOT NULL DEFAULT '{}',
    error        TEXT
);

CREATE INDEX idx_task_exec_task ON task_execution_log(task_id);

-- 注：MCP Server 表已移至 V16__create_mcp_server.sql（含 transport/args/headers/timeout_ms 等完整字段）
