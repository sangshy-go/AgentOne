-- ============================================================
-- V2: Agent 表 + Skill 表 + 绑定关系
-- ============================================================

-- Agent 表
CREATE TABLE agent (
    id               VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id     VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    name             VARCHAR(100) NOT NULL,
    description      TEXT,
    category         VARCHAR(50),
    status           VARCHAR(20)  NOT NULL DEFAULT 'draft',  -- draft / testing / published / stopped / archived
    agents_md        TEXT,
    model_config     JSONB        NOT NULL DEFAULT '{}',
    memory_config    JSONB        NOT NULL DEFAULT '{}',
    advanced_config  JSONB        NOT NULL DEFAULT '{}',
    icon             VARCHAR(10),
    avatar_url       VARCHAR(500),
    current_version  INT          NOT NULL DEFAULT 1,
    created_by       VARCHAR(36),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_workspace ON agent(workspace_id);
CREATE INDEX idx_agent_status    ON agent(status);

-- Skill 表
CREATE TABLE skill (
    id            VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id  VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    name          VARCHAR(100) NOT NULL,
    type          VARCHAR(20)  NOT NULL,  -- builtin / api / function / mcp / market
    source        VARCHAR(50),
    description   TEXT,
    input_schema  JSONB        NOT NULL DEFAULT '{}',
    output_schema JSONB        NOT NULL DEFAULT '{}',
    config        JSONB        NOT NULL DEFAULT '{}',
    version       VARCHAR(20)  NOT NULL DEFAULT '1.0.0',
    status        VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active / disabled / deprecated
    installed_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_workspace ON skill(workspace_id);
CREATE INDEX idx_skill_type      ON skill(type);

-- Agent-Skill 绑定表
CREATE TABLE agent_skill_binding (
    id              VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id        VARCHAR(36)  NOT NULL REFERENCES agent(id),
    skill_id        VARCHAR(36)  NOT NULL REFERENCES skill(id),
    skill_version   VARCHAR(20),
    config_override JSONB        NOT NULL DEFAULT '{}',
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_skill_agent ON agent_skill_binding(agent_id);
CREATE INDEX idx_agent_skill_skill ON agent_skill_binding(skill_id);
