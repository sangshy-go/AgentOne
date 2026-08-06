-- ============================================================
-- V11: 知识库增加分块策略配置
-- 支持按长度 / 按标题 / 按段落三种分块策略，
-- 以及每知识库自定义 chunkSize / chunkOverlap
-- ============================================================

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS chunk_strategy VARCHAR(20) NOT NULL DEFAULT 'by-length',
    ADD COLUMN IF NOT EXISTS chunk_size     INTEGER     NOT NULL DEFAULT 400,
    ADD COLUMN IF NOT EXISTS chunk_overlap  INTEGER     NOT NULL DEFAULT 60;
