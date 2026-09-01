# AgentOne Skill 中心 v2 PRD v1.0

> 阶段：需求分析（requirement-analysis）｜日期：2026-08-12
> 上游产物：原型 `docs/ui-demo/`（index.html / page-create.html）、流程图 `flow-diagrams.md`、
> 阶段说明与治理专题 `docs/prototype-summary.md`、质检 `docs/ui-demo/checklist.md`
> 本文不重复原型已表达的页面细节，交互以原型为准；重点是**收敛决策 + 定义后端改造**。

---

## 1. 项目概述

- **项目名称**：Skill 中心 v2（技能广场 + 我的技能 + 创建多入口 + 治理）
- **立项背景**：现 Skill 中心是「技能资产管理」视角（列表/创建/调试并列），面向技术人员；
  产品定位为**中小企业所有工种**（市场/销售/客服/人事/财务/法务/行政/数据/IT）的技能平台。
- **核心问题**：业务用户带着业务问题来，却找不到「能解决我问题的技能」；调试等技术能力占据主视角。
- **目标用户**：
  - 业务用户（发现/试用/使用，不写代码）
  - 技能创建者（业务沉淀 SOP / 技术打包脚本与 API）
  - IT/管理员（MCP 接入、发布治理、安全把关）
- **核心价值主张**：3 步用上技能（发现→试用→启用）；技能形态对齐行业标准（SKILL.md + scripts + resources）。

---

## 2. 功能需求

| 模块 | 功能点 | 优先级 | 用户故事 | 验收标准 |
|------|--------|--------|---------|---------|
| 广场 | 工种导航 chips（10 工种）筛选 | P0 | 业务用户按「我是做什么的」找技能 | 点击 chip 实时过滤；与搜索可叠加 |
| 广场 | 搜索（名称/描述/关键词） | P0 | 带明确诉求的用户直达技能 | 模糊匹配、实时；无结果有空态 |
| 广场 | 官方精选区（筛选时隐藏） | P0 | 无明确诉求时「逛」精选 | 默认展示；少量官方演示技能 |
| 广场 | 技能卡片网格 + 来源标识 | P0 | 快速判断技能是否可信/适用 | 展示 官方/自建/导入/内置/MCP |
| 详情 | 抽屉：它能做什么 + 你可以这样问 | P0 | 靠例子秒懂用法 | 示例气泡≥1 条；ESC/遮罩/按钮可关 |
| 详情 | 试一试（按 inputSchema 渲染表单） | P0 | 启用前先验证效果 | 返回结果+耗时；必填校验；写审计 |
| 详情 | 启用到 Agent（下拉选择） | P0 | 把技能挂到自己的 Agent | 复用 agent_skill_binding；Toast 反馈 |
| 详情 | 技能包文件树（脚本包） | P0 | 看清脚本包含什么 | 展示 SKILL.md+scripts+resources；标「脚本暂不执行」 |
| 详情 | 高级调试（折叠行内） | P1 | 技术用户做参数预检/执行计划 | 复用三步向导能力，行内化 |
| 我的 | 启用/停用 toggle | P0 | 临时下线技能不删除 | 渐变态/灰态正确；内置/MCP 仅可启停 |
| 我的 | 使用次数统计 | P0 | 判断技能价值 | 按真实调用计（skill_call_log） |
| 我的 | 编辑 / 导出 / 删除 | P0 | 维护自己的技能 | 删除被绑定拦截（5005）；导出 agentone-skill JSON |
| 创建 | 五入口：模板/空白/导入技能包/脚本包/API | P0 | 不同角色用合适方式创建 | 模板预填；API 结构化表单 |
| 创建 | 模板库（按工种分组） | P0 | 业务用户快速起步 | 选中预填名称/简介/工种/正文骨架 |
| 创建 | **导入技能包**：文件夹/zip/单文件/粘贴 | P0 | 外包成品技能直接导入 | 自动识别内容型 vs 脚本包；渲染文件树 |
| 创建 | 脚本包 Scripts | P0 | 技术同学打包脚本+方法论 | SKILL.md 必需；「脚本暂不执行」提示 |
| 治理 | MCP 工具「发布到广场」开关，默认关闭 | P0 | IT 控制哪些工具暴露给业务用户 | 未发布不出现在广场；已发布才可绑定 |
| 治理 | 动作型技能执行确认（草稿→确认→执行） | P0 | 有副作用的操作需人工把关 | 未确认不执行；执行写审计 |
| P1 | 工种词表工作空间可配置 | P1 | 不同行业工种差异大 | 默认 10 工种可增删 |
| P1 | OpenAPI/Swagger 导入解析 | P1 | IT 批量接入已有 API | 单接口→技能 |
| P1 | 统计区分试用 vs 真实调用 | P1 | 更准的价值度量 | call_log 带来源字段 |
| P1 | MCP Server 级批量发布开关 | P1 | 减少 IT 操作成本 | 服务端默认值可配 |
| P2 | 用户自建模板 | P2 | 团队沉淀复用 | 模板可设为空间共享 |
| P2 | 广场发布审核流 | P2 | 管理员把关上架 | 提交→审核→上架 |
| P2 | 脚本沙箱执行 | P2 | 脚本真正运行 | 沙箱隔离；导入脚本先审后执行 |

---

## 3. 非功能需求

| 维度 | 要求 |
|------|------|
| 性能 | 广场列表/搜索 < 500ms；试一试超时沿用技能配置（默认 5s） |
| 安全 | 导入 zip 防路径穿越（zip slip）；API 技能 SSRF 防护沿用；**脚本本期不执行**；写操作鉴权 + 工作空间隔离 |
| 治理 | 动作型技能执行确认 + 审计留痕（skill_call_log）；MCP 发布默认关闭 |
| 兼容 | Chrome/Safari/Firefox 最新两版；375px / 1280px 响应式 |
| 可用 | 空态/错误态齐全（广场无结果、我的技能为空、导入缺 SKILL.md 拦截） |
| 一致 | MCP 工具与自建技能同走「发布→广场」流程，消除「跳过发布直接上架」的不一致 |

---

## 4. 数据模型

在现有表（`skill` / `agent_skill_binding` / `skill_call_log` / `mcp_server`，迁移至 V18）基础上：

1. **`skill.category` 语义改为工种词表**（市场/销售/客服/人事/财务/法务合规/行政/数据分析/IT 集成/其他）。
   沿用课题⑧「前端维护词表、后端不强校验」模式；改 `SkillCategories` 默认值（OFFICE→对应工种），V19 迁移刷存量数据。
2. **新增 `skill_package_file`**（脚本包/导入技能包的文件存储）：
   `id, skill_id, path, kind(script/resource/doc), size, content(text/bytea), created_at`。
   一个 skill 一对多；SKILL.md 为 kind=doc 且必需。
3. **新增 `mcp_tool_publish`**（MCP 发布治理）：
   `id, workspace_id, server_id, tool_name, published(bool,默认 false), updated_at`，
   唯一约束 `(workspace_id, server_id, tool_name)`。广场/绑定查询 join 此表过滤 `published=true`。
4. **动作型标记**：`skill.config`(JSONB) 增加 `actionType:true`（MCP 工具按工具注解/名单推断），驱动执行确认链路。
5. 复用：启用=绑定（`agent_skill_binding`）；统计/审计=`skill_call_log`（P1 加 `source` 字段区分试用/真实）。

---

## 5. 用户交互设计

以已确认原型为准，不重复描述：
- 广场/我的/详情抽屉/试一试/启用 → `docs/ui-demo/index.html`
- 创建五入口/导入技能包 → `docs/ui-demo/page-create.html`
- 跳转与序列图 → `docs/ui-demo/flow-diagrams.md`

---

## 6. 接口/集成设计（初版）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 广场列表 | GET | /api/v1/skills/plaza?cat=&q= | 过滤 status=active 且（非 MCP 或 published=true） |
| 导入技能包 | POST | /api/v1/skills/import-package | multipart（zip 或多文件）；解析 frontmatter；落 skill + skill_package_file；zip slip 防护 |
| MCP 工具列表 | GET | /api/v1/mcp/servers/{id}/tools | 返回工具 + published 字段 |
| MCP 发布开关 | PUT | /api/v1/mcp/servers/{id}/tools/{toolName}/publish | body {published}；默认关闭 |
| 动作型执行 | POST | /api/v1/skills/{id}/invoke | 两阶段：无 confirmToken 返回草稿+token；带 token 才真实执行并审计 |
| 沿用 | - | /skills CRUD、/skills/{id}/test、bindings | 不破坏现有契约 |

---

## 7. 版本规划

| 版本 | 目标 | 范围 | 周期 |
|------|------|------|------|
| v1.0 MVP | 跑通发现→试用→启用 + 五入口创建 + 治理底线 | 全部 P0 | ~1 周（接 Phase 2 余量） |
| v1.1 | 可配置与效率 | P1 | 0.5 周 |
| v1.2 | 生态与执行 | P2（含沙箱） | 另立专项 |

---

## 8. 决策记录（收敛 prototype-summary 第 5/6 节）

| # | 问题 | 决策 | 理由 |
|---|------|------|------|
| 1 | 工种词表治理 | MVP 固定 10 工种；可配置放 P1 | 先验证导航模型，配置化有维护成本 |
| 2 | 模板库归属 | 官方维护随版本更新；自建模板 P2 |  MVP 聚焦导入与创建主链路 |
| 3 | 启用到 Agent 语义 | = 绑定，复用 agent_skill_binding；多 Agent 可绑 | 零新增模型；解绑在 Agent 页/我的技能 |
| 4 | 脚本执行 | 本期仅存储分发；沙箱 P2；导入脚本先审后执行 | 无沙箱不执行任意脚本（安全红线，Day16 决策） |
| 5 | OpenAPI 导入 | 本期结构化表单+提示；解析放 P1 | 控制 MVP 范围 |
| 6 | 统计口径 | MVP 按真实调用计；来源区分 P1 | 先用现有 call_log |
| 7 | 广场发布权限 | 创建者可发本空间广场；管理员可下架；审核 P2 | 平衡效率与管控 |
| 8 | MCP 发布治理 | 工具级开关、默认关闭 | 与其他来源「预览→发布」对齐；防危险工具自动上架 |
| 9 | 动作型确认 | 草稿→确认→执行 + 审计，随课题⑥ RBAC 分级 | 有副作用操作必须人工把关 |
