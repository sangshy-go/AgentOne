-- ============================================================
-- V20: 文档重复上传的原子性保障（P3）
--
-- 背景：上传接口用 "selectCount(knowledge_id, name, size) > 0" 判重，
-- 该检查与随后的 INSERT 不在同一原子操作内，并发上传同一文件时两个请求会双双通过检查，
-- 产生重复文档 → 重复分块 → 重复向量与 Embedding 费用浪费。
--
-- 方案：在 document 表上建 (knowledge_id, name, size) 唯一索引，把互斥下推到 DB；
-- 应用层捕获 DuplicateKeyException 转为"请勿重复上传"业务错误
-- （见 KnowledgeServiceImpl.uploadDocument）。
--
-- 兼容存量数据：若库中已存在重复行，直接建唯一索引会失败并阻塞启动迁移，
-- 故先探测，有重复时仅告警跳过（不删除任何数据），待人工清理后可重跑本迁移逻辑。
-- ============================================================

DO $$
DECLARE
    dup_groups INTEGER;
BEGIN
    SELECT COUNT(*) INTO dup_groups
    FROM (
        SELECT knowledge_id, name, size
        FROM document
        GROUP BY knowledge_id, name, size
        HAVING COUNT(*) > 1
    ) dups;

    IF dup_groups > 0 THEN
        RAISE WARNING '存在 % 组重复文档 (knowledge_id, name, size)，跳过创建唯一索引 uk_document_kb_name_size；请清理重复数据后手动创建。', dup_groups;
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uk_document_kb_name_size
            ON document (knowledge_id, name, size);
    END IF;
END $$;
