# AgentOne Phase 2 技术总结报告

> **阶段**: Phase 2 - 能力补全（Skill 生态 + MCP 集成 + IM Bot + 企业化）
> **时间**: Day 15 - Day 21（2026-08-07 至 2026-08-29）
> **分支**: `phase-2`
> **完成度**: 代码 100%（真实 LLM 回复链路待配额恢复后补验）

---

## 1. 阶段概览

### 目标
在 Phase 1 MVP（配模型 → 建知识库 → 创建 Agent → 对话 → API 接入）之上补全四类能力：
1. **Skill 生态**：工具化、技能广场、内容型技能、调试器、评测准备
2. **MCP 集成**：Server 管理、协议连接、服务发现、工具注册
3. **IM Bot 网关**：钉钉/企微适配、回调、凭证加密、多轮会话
4. **企业化**：RBAC 四角色、监控可观测、审计日志、发布双人审批

### 关键数据
| 指标 | 数值 |
|------|------|
| 完成课题 | 10 个（①-⑩）+ 3 个插入课题（内容型 Skill / 企业化 / 测试期门禁） |
| 单元测试 | 270+ 全绿（Phase 1 末约 60 → 270+） |
| 后端模块 | 7 → 9（新增 `agentone-im`；knowledge 模块拆分独立） |
| 数据库迁移 | V15 - V24（10 个） |
| 错误码段 | 5001-5016（Skill）、5200-5206（IM）、8001-8009（审批）、6019（模型调用） |

---

## 2. 架构全景

```
┌─────────────────────────────────────────────────────────────┐
│                        agentone-web（Vue 3）                 │
│  Skill 广场 / MCP 管理 / IM Bot / 监控 / 审批 / 成员管理       │
└──────────────────────────┬──────────────────────────────────┘
                           │ /api/**（Cookie/JWT + RBAC Filter 链）
┌──────────────────────────┴──────────────────────────────────┐
│ Filter 链: JwtAuth(@1) → WorkspaceRbac(@2) → Audit(@3)      │
├──────────────┬──────────────┬───────────────┬───────────────┤
│ agentone-    │ agentone-    │ agentone-im   │ agentone-     │
│ agent        │ skill        │ (IM 网关)      │ apikey        │
│ 对话/审批/监控 │ 技能生态      │ 钉钉/企微回调   │ /v1 开放接口   │
│      ↓ Toolkit(function calling) ↑                          │
│  AgentScope ReActAgent ←→ SkillRegistry（用户Skill/builtin/  │
│                            MCP 虚拟工具统一挂载）              │
├──────────────┬──────────────┴───────────────┴───────────────┤
│ agentone-    │ McpConnectionManager（stdio/sse/http）        │
│ knowledge    │ VectorStore(PgVector) + Tika + 分块            │
└──────────────┴──────────────────────────────────────────────┘
```

### 各模块职责（Phase 2 新增/变化）

| 模块 | 职责 | 关键类 |
|------|------|--------|
| `agentone-skill` | Skill CRUD/广场/导入导出/调试/两阶段确认 | `SkillServiceImpl`、`PromptSkillExecutor`、`ConfirmTokenStore` |
| `agentone-im` | IM Bot 管理、平台协议、回调路由 | `ImBotServiceImpl`、`DingTalkSender`、`WecomCrypto` |
| `agentone-agent` | 对话引擎扩展（Skill 工具化）、审批状态机、监控 | `SkillAgentTool`、`PublishRequestService`、`MonitorService` |
| `agentone-common` | Filter 链、审计、租户拦截器 | `AuditFilter`、`WorkspaceRbacFilter` |

### 模块协作的关键设计
**Skill 统一挂载**：用户 Skill（api/prompt）、builtin、MCP 工具三种来源全部收敛为 AgentScope Toolkit 的 function tool，LLM 按统一协议调用；来源差异由 `SkillRegistry` 路由，对话引擎无感知。

---

## 3. 十个课题交付清单

| 课题 | 交付 | 技术文档 |
|------|------|---------|
| ① Skill 工具化 | 提示词注入 → function calling；skill_call_log 调用链留痕；builtin 虚拟挂载 | 04 §3.1-3.4 |
| ② Skill 中心 | CRUD/测试/启动加载，错误码 5001-5008 | 04 §3.5 |
| ③ Skill 调试器 | 三步向导 + 参数预检 + 调试审计 | 04 §3.6 |
| ④ MCP 集成 | Server CRUD、stdio/sse/http 连接、工具发现注册、启动重连 | 06 全文 |
| ⑤ IM Bot 网关 | 凭证 AES-256-GCM 加密、钉钉加签、企微协议、回调路由、多轮会话映射 | 07 全文 |
| ⑥ 监控 + RBAC | 仪表盘/对话日志/调用链三 Tab；四角色 + 每请求查库即时生效 | 05 §1-5 |
| ⑦ 内容型 Skill | type=prompt 渐进式披露；导出/导入 | 04 §3.8 |
| ⑧ 企业化升级 | 工种分类词表、搜索筛选、创建双轨制（模板库 + 结构化表单） | 04 §3.9 |
| ⑨ Skill 广场 | 发现/治理双口径、技能包导入（zip-slip 防护）、动作型两阶段确认、MCP 发布治理 | 04 §3.11-3.16 |
| ⑩ 审计 + 审批 | AuditFilter 全量写留痕；PENDING_REVIEW 状态机；双人原则；auditor 角色 | 05 §6-8 |

**插入修复课题**：模型配置 fail-fast（6018）、对话附件上传、测试期门禁收敛、会话自动标题、API 错误语义（404/6019）——均出自整合测试/用户反馈。

---

## 4. 关键设计决策

| 决策 | 选择 | 理由 | 否决的备选 |
|------|------|------|-----------|
| Skill 调用方式 | function calling（Toolkit） | LLM 自主决策调用时机；提示词注入无法精确触发 | 继续提示词注入 |
| 脚本型 Skill | 不做（仅存储不执行） | 沙箱安全成本过高，红线 | 自建脚本沙箱 |
| MCP 协议层 | 复用 AgentScope Registrar | 不自研协议解析 | 手撸 MCP 客户端 |
| IM 凭证存储 | AES-256-GCM 加密列，接口只回掩码 | 数据库泄漏不泄密 | 明文/仅掩码展示 |
| 企微/飞书 | 后端完整、前端「待建设」 | 交付口径 = 真实联调通过，未联调不宣传 | 半成品上架 |
| 发布流程 | 双人审批状态机，删直达 publish | 合规赛道硬需求；提交人不可自审 | 多级审批链（过度设计） |
| 审计 detail | 只记 method/path/operator，不记请求体 | 请求体含 API Key/IM 凭证，落库即泄密 | 全量留痕 |
| 平台级超管 | 不做 | 击穿租户隔离，合规负资产 | 超级管理员 |
| 测试动作可见性 | 测试期门禁（发布/启用后收起测试入口） | 测试动作只属于测试期，防生产误操作 | 常驻测试按钮 |

---

## 5. 测试与验证状态

| 层 | 状态 |
|----|------|
| 单元测试 | 270+ 全绿，覆盖加密器/签名/状态机/Filter/Service |
| 模拟 E2E | Python 脚本模拟钉钉/企微回调 7 场景；curl 全链路（审批三视角/错误码矩阵） |
| 浏览器实测 | 每课题交付前实测（提交人/审批人/审计员三视角、门禁显隐、会话隔离） |
| 整合测试（08-29） | LLM 无关链路全绿；发现并修复未知路由 500→404、同步对话裸 500→6019 |
| **未验证项** | 真实 LLM 回复（配额 09-02 重置）、钉钉真实 @对话联调（等凭证）、企微真实联调 |

---

## 6. 你应该掌握的

### 核心概念
1. **Skill 统一挂载**：三种来源（用户/builtin/MCP）→ 一套 function tool，差异在 Registry 路由
2. **测试期门禁**：创建即停用/草稿，测试入口只在测试期可见，发布/启用即收起
3. **双人审批**：提交人不可自审（含 owner），config_snapshot 保证审什么=发什么
4. **审计三不记**：不记请求体（防泄密）、不记失败请求（边界拦截已有日志）、不阻断业务（best-effort）

### 代码阅读路线
- Skill 调用链：`SkillAgentTool.call()` → `SkillRegistry` 路由 → `ApiSkillExecutor`/`PromptSkillExecutor`
- IM 回调：`ImCallbackController` → `ImBotServiceImpl.handleIncoming()` → 复用 `ChatService` 同步对话
- 审批：`PublishRequestService`（状态机 + 双人校验）+ `AgentStatus`（合法转移表）
- 每课题详解见 `docs/daily-log/` 对应日志（均含代码阅读指南）

---

## 7. 阶段反思

### 做得好的
- **课题制 + 每日日志**：10 个课题每个都有独立日志 + 代码阅读指南，回溯成本极低
- **交付口径诚实**：企微/飞书未联调即标「待建设」，不做半成品宣传
- **整合测试找真问题**：错误语义缺陷（404/6019）只在异常路径暴露，正常路径单测覆盖不到

### 需要改进的
- **检索质量欠账**：RAG 仍是 MVP 水平（无混合检索/重排/评测），详见 `docs/technical/improvements.md` A1 区
- **真实联调滞后**：钉钉/真实 LLM 验证拖到阶段末，应更早引入真实凭证
- **文档双写**：rest-api.md 与代码错误码偶发不同步（6019 之前 404 约定与实现不一致）

### 对 Phase 3 的输入
1. **中台集成四件套**（管理面 OpenAPI / 外部用户身份透传 / 嵌入式对话组件 / 事件回调）——当前只有 `/v1/chat`，业务平台无法程序化接入
2. **RAG 质量栈**（混合检索 + 重排 + 评测体系 + knowledge 模块单测）
3. **记忆与评测**（AE-A5 长期记忆、AE-F Skill 评测）——改进总账见 `docs/technical/improvements.md`

---

## 8. 深入阅读

| 主题 | 文档 |
|------|------|
| Skill 系统 | docs/technical/04-skill-system.md |
| 认证/RBAC/审计/审批 | docs/technical/05-auth-rbac.md |
| MCP 集成 | docs/technical/06-mcp-integration.md |
| IM Bot 网关 | docs/technical/07-im-bot-gateway.md |
| API 参考 | docs/api/rest-api.md |
| 改进总账 | docs/technical/improvements.md |

---

**生成日期**: 2026-08-29（M6 收口）
