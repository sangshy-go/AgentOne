-- ============================================================
-- V1: 用户表 + 工作空间表
-- ============================================================

-- 用户表
CREATE TABLE sys_user (
    id          VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    email       VARCHAR(200) NOT NULL UNIQUE,
    password    VARCHAR(200) NOT NULL,
    nickname    VARCHAR(100),
    avatar_url  VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active / locked / disabled
    login_fail_count INT     NOT NULL DEFAULT 0,
    locked_until   TIMESTAMP,
    last_login_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_sys_user_email ON sys_user(email);

-- 工作空间表
CREATE TABLE workspace (
    id          VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    owner_id    VARCHAR(36)  NOT NULL REFERENCES sys_user(id),
    settings    JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP
);

CREATE INDEX idx_workspace_owner ON workspace(owner_id);

-- 用户-工作空间关联表（多对多）
CREATE TABLE user_workspace (
    id           VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      VARCHAR(36) NOT NULL REFERENCES sys_user(id),
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(id),
    role         VARCHAR(20) NOT NULL DEFAULT 'member',  -- owner / admin / member
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    UNIQUE(user_id, workspace_id)
);

CREATE INDEX idx_user_workspace_user ON user_workspace(user_id);
CREATE INDEX idx_user_workspace_ws   ON user_workspace(workspace_id);
