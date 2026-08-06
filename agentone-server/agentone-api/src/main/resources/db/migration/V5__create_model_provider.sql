-- ============================================================
-- V5: 模型供应商管理
-- ============================================================

-- 模型供应商表
CREATE TABLE model_provider (
    id           VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    name         VARCHAR(100) NOT NULL,                     -- 显示名称，如 "OpenAI 官方"
    provider     VARCHAR(50)  NOT NULL,                     -- openai / deepseek / dashscope / custom
    api_key      VARCHAR(500) NOT NULL,                     -- API Key（加密存储）
    base_url     VARCHAR(500) NOT NULL DEFAULT 'https://api.openai.com',
    model_name   VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-mini',  -- 默认模型名称
    status       VARCHAR(20)  NOT NULL DEFAULT 'active',    -- active / disabled
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_model_provider_workspace ON model_provider(workspace_id);

-- 知识库关联模型供应商（Embedding 用）
ALTER TABLE knowledge_base ADD COLUMN model_provider_id VARCHAR(36) REFERENCES model_provider(id);

-- Agent 关联模型供应商（对话用）
ALTER TABLE agent ADD COLUMN model_provider_id VARCHAR(36) REFERENCES model_provider(id);
