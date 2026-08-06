-- ============================================================
-- model_provider 表增加 embedding_model 字段
-- 原因：聊天模型（如 qwen3.7-max）和 embedding 模型（如 text-embedding-v3）
--       是两种不同的能力，不能混用同一个字段。知识库向量化必须用专门的 embedding 模型。
-- 方案：新增 embedding_model 字段，为空时 fallback 到 model_name（向下兼容）
-- ============================================================

-- 如果列不存在则添加
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'model_provider' AND column_name = 'embedding_model'
    ) THEN
        ALTER TABLE model_provider ADD COLUMN embedding_model VARCHAR(100);
    END IF;
END $$;
