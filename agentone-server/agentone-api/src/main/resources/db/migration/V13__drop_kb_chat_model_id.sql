-- 移除知识库表的 chat_model_id 列（死字段：写入后从未被业务逻辑读取，
-- Agent 推理用的 chat 模型由 Agent 自身的 ModelConfig 管理，与知识库无关）
ALTER TABLE knowledge_base DROP COLUMN IF EXISTS chat_model_id;
