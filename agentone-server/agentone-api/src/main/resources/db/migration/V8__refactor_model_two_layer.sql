-- ============================================================
-- 重构模型配置为两层结构（Provider + Model）
-- 原因：业界标准做法是 Provider 只存凭证，Model 存具体模型（按类型区分）
--       聊天模型和 embedding 模型是完全不同的能力，必须分开管理
-- 方案：新增 model 表，model_provider 去掉 model_name/embedding_model 字段
-- ============================================================

-- 1. 创建 model 表
CREATE TABLE IF NOT EXISTS model (
    id VARCHAR(36) PRIMARY KEY,
    provider_id VARCHAR(36) NOT NULL REFERENCES model_provider(id) ON DELETE CASCADE,
    model_type VARCHAR(20) NOT NULL,  -- 'chat' | 'embedding' | 'rerank' | 'image2text'
    model_id VARCHAR(100) NOT NULL,   -- API 调用时的 model 字段值
    display_name VARCHAR(100),        -- 显示名称
    context_size INT DEFAULT 4096,    -- 上下文长度
    max_tokens INT DEFAULT 2048,      -- 最大响应长度（仅 chat 类型）
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_model_provider ON model(provider_id);
CREATE INDEX IF NOT EXISTS idx_model_type ON model(model_type);

-- 2. 迁移现有数据：model_provider.model_name → model 表（chat 类型）
INSERT INTO model (id, provider_id, model_type, model_id, display_name, created_at)
SELECT
    md5(id || '_chat') AS id,  -- 生成稳定的 ID
    id AS provider_id,
    'chat' AS model_type,
    model_name AS model_id,
    name AS display_name,
    now() AS created_at
FROM model_provider
WHERE model_name IS NOT NULL AND model_name != ''
ON CONFLICT (id) DO NOTHING;

-- 3. 迁移 embedding_model（如果有）→ model 表（embedding 类型）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'model_provider' AND column_name = 'embedding_model'
    ) THEN
        EXECUTE '
            INSERT INTO model (id, provider_id, model_type, model_id, display_name, created_at)
            SELECT
                md5(id || ''_embedding'') AS id,
                id AS provider_id,
                ''embedding'' AS model_type,
                embedding_model AS model_id,
                name || '' Embedding'' AS display_name,
                now() AS created_at
            FROM model_provider
            WHERE embedding_model IS NOT NULL AND embedding_model != ''''
            ON CONFLICT (id) DO NOTHING
        ';
    END IF;
END $$;

-- 4. model_provider 表去掉 model_name 和 embedding_model 字段
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'model_provider' AND column_name = 'model_name'
    ) THEN
        ALTER TABLE model_provider DROP COLUMN model_name;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'model_provider' AND column_name = 'embedding_model'
    ) THEN
        ALTER TABLE model_provider DROP COLUMN embedding_model;
    END IF;
END $$;

-- 5. 更新 knowledge_base 表
-- 新增 chat_model_id 和 embedding_model_id 字段（绑定具体的 model，而不是 provider）
-- 保留 model_provider_id 作为 fallback（向下兼容）
DO $$
BEGIN
    -- 添加 chat_model_id 字段
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'knowledge_base' AND column_name = 'chat_model_id'
    ) THEN
        ALTER TABLE knowledge_base ADD COLUMN chat_model_id VARCHAR(36);
    END IF;

    -- 添加 embedding_model_id 字段
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'knowledge_base' AND column_name = 'embedding_model_id'
    ) THEN
        ALTER TABLE knowledge_base ADD COLUMN embedding_model_id VARCHAR(36);
    END IF;
END $$;
