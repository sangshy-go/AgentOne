-- RBAC 角色模型（课题⑥）
-- 规格 §13 预置 4 角色：管理员(admin) / 开发者(developer) / 观察者(observer) / API 用户。
-- "API 用户"不是控制台角色（仅通过 API Key 调用，不登录控制台），故 user_workspace
-- 中的控制台角色为 owner / admin / developer / observer 四种。

-- 1. 存量 'member' 归一为 'developer'（语义一致：可创建编辑资源的普通成员）
UPDATE user_workspace SET role = 'developer' WHERE role = 'member';

-- 2. 收敛角色取值，防止未来写入非法角色串（CHECK 失败即可发现而非静默）
ALTER TABLE user_workspace
    ADD CONSTRAINT ck_user_workspace_role
    CHECK (role IN ('owner', 'admin', 'developer', 'observer'));

-- 3. 新成员默认角色：开发者
ALTER TABLE user_workspace ALTER COLUMN role SET DEFAULT 'developer';
