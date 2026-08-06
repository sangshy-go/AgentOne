-- ============================================================
-- V10: 删除 document_chunk 表的 embedding 列
-- 背景：该列从 V3 创建以来从未被代码写入，所有向量操作走的是
--       Spring AI 管理的 vector_store / vector_store_{dim} 表。
--       保留该列会误导开发者以为向量数据存在此处。
-- ============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'document_chunk' AND column_name = 'embedding'
    ) THEN
        ALTER TABLE document_chunk DROP COLUMN embedding;
    END IF;
END $$;
