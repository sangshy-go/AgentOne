-- ============================================================
-- V23: IM Bot 网关（课题⑤）
-- im_bot: 机器人配置。凭证为第三方密钥（可发送消息/访问企业接口），
--         故 config 整体 AES-GCM 加密存储（密钥环境变量 AGENTONE_IM_SECRET_KEY），
--         不存明文、接口不回传（VO 只给掩码摘要）。
-- im_sender_session: 发送者→会话映射，保证 IM 多轮对话记忆。
-- ============================================================

CREATE TABLE im_bot (
    id               VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id     VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    name             VARCHAR(100) NOT NULL,
    platform         VARCHAR(20)  NOT NULL,  -- dingtalk / wecom
    mode             VARCHAR(20)  NOT NULL,  -- webhook（仅发送）/ callback（收发，企微自建应用或钉钉企业机器人回调）
    agent_id         VARCHAR(36),            -- 绑定的 Agent（可空：纯通知机器人）
    config_encrypted TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active / disabled
    created_by       VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_im_bot_platform CHECK (platform IN ('dingtalk', 'wecom')),
    CONSTRAINT ck_im_bot_mode     CHECK (mode IN ('webhook', 'callback')),
    CONSTRAINT ck_im_bot_status   CHECK (status IN ('active', 'disabled'))
);

CREATE INDEX idx_im_bot_workspace ON im_bot(workspace_id);

-- 发送者会话映射（bot 删除时级联清理）
CREATE TABLE im_sender_session (
    bot_id     VARCHAR(36)  NOT NULL REFERENCES im_bot(id) ON DELETE CASCADE,
    sender_id  VARCHAR(200) NOT NULL,
    session_id VARCHAR(36)  NOT NULL,
    PRIMARY KEY (bot_id, sender_id)
);
