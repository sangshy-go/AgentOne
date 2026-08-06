-- ============================================================
-- 修复 vector_store 表 id 列类型
-- 原因：业务侧使用 MyBatis-Plus 的 ASSIGN_UUID（32 位 hex，不带连字符）
--       Spring AI PgVectorStore 默认按 UUID 解析会抛 IllegalArgumentException
-- 方案：将 id 列从 uuid 改为 text，并在 application.yml 配置 id-type: TEXT
-- 幂等：已执行过则跳过（兼容手动 ALTER 过的环境）
-- ============================================================

-- 如果 id 列还不是 text，改成 text
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'vector_store' AND column_name = 'id' AND data_type <> 'text'
    ) THEN
        ALTER TABLE vector_store ALTER COLUMN id TYPE text;
    END IF;
END $$;

-- 去掉 uuid_generate_v4() 默认值，改由应用侧（Spring AI）显式传入 ID
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'vector_store' AND column_name = 'id'
          AND column_default IS NOT NULL
    ) THEN
        ALTER TABLE vector_store ALTER COLUMN id DROP DEFAULT;
    END IF;
END $$;
