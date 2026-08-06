-- S3: 为绑定表补充 workspace_id 并纳入多租户隔离
-- 背景：原 agent_knowledge_binding / agent_skill_binding 在 WorkspaceInterceptor 白名单中，
--       不自动追加 workspace 过滤，且无归属校验，导致可跨租户绑定知识库（RAG 泄露）、越权解绑。
-- 修复：补充 workspace_id 列（按父实体回填），从白名单移除后由拦截器自动注入与过滤。

-- 1. agent_knowledge_binding：通过 knowledge_base 反查 workspace
ALTER TABLE agent_knowledge_binding ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE agent_knowledge_binding
SET workspace_id = sub.ws
FROM (
    SELECT b.id AS bid, k.workspace_id AS ws
    FROM agent_knowledge_binding b
    JOIN knowledge_base k ON k.id = b.knowledge_id
) sub
WHERE agent_knowledge_binding.id = sub.bid;

-- 2. agent_skill_binding：通过 agent 反查 workspace
ALTER TABLE agent_skill_binding ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE agent_skill_binding
SET workspace_id = sub.ws
FROM (
    SELECT b.id AS bid, a.workspace_id AS ws
    FROM agent_skill_binding b
    JOIN agent a ON a.id = b.agent_id
) sub
WHERE agent_skill_binding.id = sub.bid;
