-- MCP 工具以虚拟 Skill 形式注册（课题④），ID 形如 mcp-{serverId}-{toolName}：
-- serverId 为 32 位 hex，toolName 由外部 MCP Server 定义（可达数十字符），
-- 总长超过原有 VARCHAR(36)（UUID 长度），绑定与审计落库会报 value too long。
-- skill 表自身不受影响（只存 api/market Skill，ID 仍为 UUID）；
-- agent_skill_binding.skill_id 的外键已在 V15 移除，扩列无约束冲突。
ALTER TABLE agent_skill_binding ALTER COLUMN skill_id TYPE VARCHAR(200);
ALTER TABLE skill_call_log ALTER COLUMN skill_id TYPE VARCHAR(200);
