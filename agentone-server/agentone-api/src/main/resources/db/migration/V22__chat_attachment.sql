-- ============================================================
-- V22: 对话附件表 + chat_message.attachments 列
-- ============================================================

CREATE TABLE chat_attachment (
  id          VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id VARCHAR(36) NOT NULL,
  user_id     VARCHAR(100) NOT NULL,
  file_name   VARCHAR(255) NOT NULL,
  mime_type   VARCHAR(100) NOT NULL,
  file_size   BIGINT       NOT NULL,
  kind        VARCHAR(10)  NOT NULL,  -- image / document
  data        BYTEA        NOT NULL,
  parsed_text TEXT,
  created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachment_user ON chat_attachment(user_id);

ALTER TABLE chat_message ADD COLUMN attachments JSONB;
