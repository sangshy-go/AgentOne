-- ============================================================
-- V19: Skill 中心 v2（技能广场）
--
-- 1) skill.category 语义由"业务场景"改为"工种"词表：
--    市场/销售/客服/人事/财务/法务合规/行政/数据分析/IT集成/其他
--    存量数据按语义就近映射，无法对应的归入"其他"由用户重新归类
-- 2) skill_package_file：脚本包/导入技能包的文件存储
--    （SKILL.md + scripts + resources；脚本本期仅存储不执行）
-- 3) mcp_tool_publish：MCP 工具"发布到广场"治理（默认关闭）
-- ============================================================

-- 1. 存量分类映射（旧词表 → 工种词表）
UPDATE skill SET category = '法务合规' WHERE category = '风控合规';
UPDATE skill SET category = '人事'     WHERE category = '人力资源';
UPDATE skill SET category = '客服'     WHERE category = '客服运营';
UPDATE skill SET category = '其他'     WHERE category = '办公效率';
-- 财务 / IT集成 / 其他 与新词表一致，无需迁移

-- 2. 技能包文件表（一个 skill 一对多；SKILL.md 为 kind=doc 且必需）
CREATE TABLE skill_package_file (
    id          VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    skill_id    VARCHAR(36)  NOT NULL REFERENCES skill(id),
    path        VARCHAR(500) NOT NULL,                 -- 包内相对路径，如 scripts/scan.py
    kind        VARCHAR(20)  NOT NULL,                 -- script / resource / doc
    size        BIGINT       NOT NULL DEFAULT 0,
    content     TEXT,                                  -- 文本内容；二进制资源为 NULL（仅存元信息）
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (skill_id, path)
);

CREATE INDEX idx_skill_package_file_skill ON skill_package_file(skill_id);

-- 3. MCP 工具发布治理表
-- MCP 工具是虚拟 Skill（不落 skill 表），发布状态独立存储；
-- 广场列表/绑定校验 join 此表过滤 published=true。默认关闭，IT 显式发布后才可见。
CREATE TABLE mcp_tool_publish (
    id            VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id  VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    server_id     VARCHAR(36)  NOT NULL,
    tool_name     VARCHAR(200) NOT NULL,
    published     BOOLEAN      NOT NULL DEFAULT false,
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, server_id, tool_name)
);

CREATE INDEX idx_mcp_tool_publish_workspace ON mcp_tool_publish(workspace_id);
