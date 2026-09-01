-- builtin Skill 采用"虚拟挂载"：不落 skill 表，只存在于 SkillRegistry（内存）。
-- agent_skill_binding.skill_id 因此可能指向 Registry 中的虚拟 Skill（如 builtin-knowledge-search），
-- 原外键会导致 builtin 绑定直接违反约束。
--
-- 移除该外键后，引用完整性改由服务层保障：
--   1. 存在性：AgentSkillServiceImpl.bind() 校验 skill_id 必须是 DB 行（api/market）
--      或 SkillRegistry 描述符（builtin），并对 DB 行做跨租户检查；
--   2. 删除保护：SkillServiceImpl.delete() 先查绑定数，仍被绑定时拒绝删除。
-- agent_id 外键保留（Agent 始终落库）。
ALTER TABLE agent_skill_binding DROP CONSTRAINT agent_skill_binding_skill_id_fkey;
