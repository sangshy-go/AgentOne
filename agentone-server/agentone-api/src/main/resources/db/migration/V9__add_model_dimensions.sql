-- ============================================================
-- V9: model 表增加 dimensions 字段（embedding 模型向量维度）
-- 背景：不同 embedding 模型输出不同维度（1536/1024/768 等）
--       向量表需按维度隔离，因此 model 表必须记录维度信息
-- ============================================================

-- 1. model 表加 dimensions 字段，默认 1536（覆盖已有数据）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'model' AND column_name = 'dimensions'
    ) THEN
        ALTER TABLE model ADD COLUMN dimensions INTEGER DEFAULT 1536;
    END IF;
END $$;
