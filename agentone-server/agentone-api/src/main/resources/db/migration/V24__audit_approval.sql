-- ============================================================
-- V24: 课题⑩ 审计日志接线 + Agent 发布审批
-- 1. user_workspace 角色 CHECK 增 auditor（审计员：只读 + 查审计，不可审批/管成员）
-- 2. agent_publish_request：发布审批申请单（配置快照 + 提交人/审核人分离，双人原则）
-- 注：audit_log 表 V4 已建（schema 无需变更），本次由 AuditFilter 代码接线激活。
-- ============================================================

-- 1. 角色约束扩展：加 auditor
--    先删旧约束再重建；存量数据无 auditor，不受影响
ALTER TABLE user_workspace DROP CONSTRAINT ck_user_workspace_role;
ALTER TABLE user_workspace
    ADD CONSTRAINT ck_user_workspace_role
    CHECK (role IN ('owner', 'admin', 'developer', 'observer', 'auditor'));

-- 2. 发布审批申请单
--    agent_id 不加 REFERENCES（对齐 im_bot.agent_id 约定）：审批记录须在 Agent 删除后仍可追溯
--    agent_name / submitter_email / reviewer_email 为提交/审核时点快照：审计记录自包含，不依赖关联查询
CREATE TABLE agent_publish_request (
    id               VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id     VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    agent_id         VARCHAR(36)  NOT NULL,
    agent_name       VARCHAR(100) NOT NULL,
    config_snapshot  JSONB        NOT NULL DEFAULT '{}',  -- 提交时 Agent 全量配置快照（审什么 = 发什么）
    status           VARCHAR(20)  NOT NULL DEFAULT 'pending',  -- pending / approved / rejected / withdrawn
    submitter_id     VARCHAR(36)  NOT NULL,
    submitter_email  VARCHAR(100),
    reviewer_id      VARCHAR(36),
    reviewer_email   VARCHAR(100),
    review_comment   TEXT,                                -- 驳回理由（必填）/ 审批意见
    submitted_at     TIMESTAMP    NOT NULL DEFAULT now(),
    reviewed_at      TIMESTAMP,
    CONSTRAINT ck_publish_request_status CHECK (status IN ('pending', 'approved', 'rejected', 'withdrawn'))
);

CREATE INDEX idx_publish_request_ws_status ON agent_publish_request(workspace_id, status);
CREATE INDEX idx_publish_request_agent     ON agent_publish_request(agent_id);
CREATE INDEX idx_publish_request_submitter ON agent_publish_request(submitter_id, status);
