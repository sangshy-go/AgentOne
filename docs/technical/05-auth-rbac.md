# 认证与权限（RBAC + 审计 + 发布审批）技术方案

> Phase 2 · 课题⑥（RBAC 成员管理）+ 课题⑩（审计日志 + Agent 发布审批）
> 状态：已交付（课题⑥ 四角色成员管理；课题⑩ 激活 audit_log + 审批状态机 + auditor 角色）
> 前置阅读：[01-architecture.md](01-architecture.md)（模块划分）

## 1. 概述

本文档覆盖 AgentOne 的"谁能做什么、做了什么留痕、关键操作双人复核"三件事：

| 能力 | 课题 | 载体 |
|------|------|------|
| 认证（注册/登录/工作空间切换） | MVP | `JwtAuthFilter` + HttpOnly Cookie |
| 工作空间角色与写拦截 | ⑥ | `WorkspaceRbacFilter`（@Order 2） |
| 成员管理（增删改角色） | ⑥ | `/api/members` + `MemberServiceImpl` |
| 变更审计（写操作全量留痕） | ⑩ | `AuditFilter`（@Order 3）→ `audit_log` |
| Agent 发布审批（双人原则） | ⑩ | `PublishRequestServiceImpl` + `agent_publish_request` |
| 审计员只读角色 | ⑩ | `auditor`（V24 CHECK 约束扩入） |

对外"可审计"宣传的落地依据：所有成功的写请求自动落 `audit_log`（§4），Agent 发布须经提交人以外的管理员审批（§5）。设计范围刻意收敛——不做多级审批链、组织树、通用审批引擎、动态权限配置（见 §8）。

## 2. 架构设计

### 2.1 过滤器链（三层 @Order）

```
HTTP 请求
   ▼
JwtAuthFilter (@Order 1)          解析 Cookie/Bearer JWT → RuntimeContext(userId, workspaceId, email)
   ▼
WorkspaceRbacFilter (@Order 2)    按请求从 DB 取角色 → Context.role；非成员 2002 / 只读角色写拦截 2004 / 成员门禁 2003
   ▼
AuditFilter (@Order 3)            chain.doFilter 之后判断：成功写请求 → 落 audit_log（best-effort）
   ▼
Controller / Service
```

顺序即依赖：RbacFilter 需要 Jwt 已建立上下文；AuditFilter 需要前两层都放行（被边界拦截的请求已有各自日志，不重复记录）且 `RuntimeContext` 就绪。

### 2.2 RuntimeContext

`ThreadLocal<Context>` 贯穿请求生命周期，字段：`userId / workspaceId / email / role`。

- `userId`、`workspaceId`、`email` 由 JwtAuthFilter 从 JWT 解出
- `role` 由 WorkspaceRbacFilter **每请求查库**写入（`user_workspace.role`）——管理员改角色即时生效，无需目标用户重登录；同请求内以 request 属性缓存，不重复查库
- IM 回调等无 JWT 场景：由业务代码注入虚拟用户上下文 `im:{platform}:{senderId}`（见 07 文档 §5）
- 租户隔离：MyBatis-Plus 租户拦截器对带 `workspace_id` 的表自动注入条件，`audit_log`、`agent_publish_request` 均带该列，天然按空间隔离

## 3. 角色矩阵（含 auditor）

角色存 `user_workspace.role`，CHECK 约束五值（V24）：

| 能力 | owner | admin | developer | observer | auditor |
|------|:----:|:----:|:----:|:----:|:----:|
| 读业务数据（Agent/知识库/Skill/监控…） | ✅ | ✅ | ✅ | ✅ | ✅ |
| 写业务数据（增删改） | ✅ | ✅ | ✅ | ❌ 2004 | ❌ 2004 |
| 提交发布审批 | ✅ | ✅ | ✅ | ❌ | ❌ 8006 |
| 审批（通过/驳回） | ✅ | ✅ | ❌ 8007 | ❌ | ❌ 8007 |
| 撤回审批（仅本人提交的） | ✅ | ✅ | ✅ | — | — |
| 查看审计日志 `/api/audit-logs` | ✅ | ✅ | ❌ 2003 | ❌ 2003 | ✅ |
| 审批列表可见范围 | 全空间 | 全空间 | 仅本人提交 | 仅本人提交 | 全空间（只读） |
| 成员管理 `/api/members` | ✅ | ✅ | ❌ 2003 | ❌ | ❌ |
| 工作空间改名/删除 | ✅ | ✅ | ❌ | ❌ | ❌ |

要点：

- **observer 与 auditor 同为写只读**（RbacFilter 统一 2004 拦截）；差别在 auditor 额外可查审计与全空间审批列表
- **auditor 不能审批**：审计角色的价值在于独立旁观，若可审批则既当运动员又当裁判
- **提交人不可自审（8003）含 owner**：单人工作空间想发布必须引入第二成员——这是合规产品（双人原则 / four-eyes）的正确姿势，不设后门
- 角色判定全部以 `RuntimeContext.getRole()` 为准，前端 `canApprove`/`canViewAudit` 仅做展示分流，**安全边界永远在后端**

## 4. 审计机制（AuditFilter）

### 4.1 记录什么

| 列 | 来源 |
|----|------|
| `workspace_id` / `operator_id` | RuntimeContext |
| `resource_type` / `resource_id` / `action` | **从路径推导**：`/api/{resource}[/{id}][/{sub}]` → 首段为 resource_type；第二段形如 UUID（32 hex 或 36）则取为 resource_id；再后一段为 action（如 `approve`）；无子路径按 HTTP 方法兜底 create/update/delete；解析不出兜底 `http:+method` 不硬猜 |
| `detail`（JSONB） | `{method, path, operatorEmail}`——operatorEmail 写入时即落库，查询免 join 用户表 |
| `created_at` | 写入时刻 |

**关键安全决策：不记请求体。** 模型 API Key、IM 凭证（钉钉加签 secret、企微 encodingAesKey）等全部经写请求体传输，落库即泄密。"变更快照"只存在于发布审批的 `config_snapshot`（§5.3），那是经评审的、自包含的配置留痕。

### 4.2 何时记

`chain.doFilter` 返回后判定，全部满足才落库：

1. 方法 ∈ {POST, PUT, DELETE, PATCH}
2. 路径不在跳过列表：`/api/auth/`、`/api/workspaces`、`/v1/`、`/api/chat`、`/api/im/callback/`、`/actuator`、`/error`（跳过理由：认证高频无空间语义 / 运行时已有 `chat_message`、`chat_api_log` 全量留痕 / 回调是虚拟用户防刷屏）
3. HTTP 状态 < 400
4. **无业务错误标记**：`GlobalExceptionHandler` 把业务异常转 Result 时 `request.setAttribute("agentone.error.code", code)`，AuditFilter 据此排除失败写请求（边界拒绝已有各自日志）
5. 上下文含 workspaceId 与 userId

不包 `ResponseWrapper` 解析响应体——会挂住 `/api/chat/stream` 的 SSE 流，故用"错误标记属性"这一轻量方案。

### 4.3 best-effort 原则

审计写入全程包在 try-catch 内，异常仅 `log.warn`，绝不阻断业务响应。审计是观测手段，不是业务依赖；`audit_log` 是追加表，索引 `(workspace_id, created_at)` 支撑按空间倒序分页。

## 5. Agent 发布审批（双人原则）

### 5.1 状态机

发布唯一路径 = 审批（原 `POST /api/agents/{id}/publish` 直达端点与 `PUBLISH` 动作枚举均已删除）：

```
DRAFT ──SUBMIT_REVIEW──→ PENDING_REVIEW ──APPROVE──→ PUBLISHED（currentVersion+1）
TESTING ──SUBMIT_REVIEW──↗                 ├─REJECT──→ DRAFT
                                           └─WITHDRAW──→ DRAFT（仅提交人）
```

`PENDING_REVIEW`（审批中）语义——**审什么 = 发什么**：

- 冻结编辑（3002）、删除（3004）、停用；先撤回申请或完成审批才解冻
- **控制台对话仍可用**（`ChatServiceImpl.loadAgent` 白名单含 PENDING_REVIEW）：审批前允许继续验证
- IM 回调维持仅转发 `PUBLISHED`：未批准的配置不流向企业群

### 5.2 端点与门禁

见 rest-api.md §17。门禁分层：

| 层 | 校验 |
|----|------|
| WorkspaceRbacFilter | observer/auditor 写请求（含 submit）→ 2004 |
| submit（Service） | 角色 ∈ {developer, admin, owner}（8006 兜底）；Agent 属本空间；状态 ∈ {DRAFT, TESTING}（8005）；无待审申请（8002） |
| approve / reject | 角色 ∈ {admin, owner}（8007）；**审核人 ≠ 提交人**（8003）；reject 必填理由（8004）；Agent 已删/归档 → 8009 |
| withdraw | 仅提交人本人（8008）；仅 pending |

approve / reject / withdraw 均 `@Transactional(rollbackFor = Exception.class)`——Agent 状态、版本号与申请单状态原子落库。

### 5.3 配置快照（config_snapshot）

submit 时把 Agent 的**配置字段**（name / description / category / icon / avatarUrl / agentsMd / modelConfig / modelProviderId / memoryConfig / advancedConfig / currentVersion）序列化入 `agent_publish_request.config_snapshot`（JSONB），剔除 status/createdAt 等运行时字段。配合审批中冻结，保证审核人看到的 = 最终发布的。

`agent_name` / `submitter_email` / `reviewer_email` 同为提交/审核时点快照：**审计记录自包含**，历史申请不因 Agent 删除或用户改名而失名（`agent_id` 刻意不加 REFERENCES，对齐 im_bot 约定）。

## 6. 数据库设计

V24 迁移（`agentone-api/src/main/resources/db/migration/V24__audit_approval.sql`）：

1. `user_workspace`：DROP 旧 CHECK 重建，role 增 `'auditor'`
2. `agent_publish_request`：

| 列 | 说明 |
|----|------|
| `id` | VARCHAR(36) DEFAULT uuid_generate_v4() |
| `workspace_id` | REFERENCES workspace(id)，租户隔离列 |
| `agent_id` / `agent_name` | 目标 Agent（不加外键）+ 名称快照 |
| `config_snapshot` | JSONB，提交时配置快照 |
| `status` | CHECK ∈ pending/approved/rejected/withdrawn |
| `submitter_id/email`、`reviewer_id/email` | 双人分离，时点快照 |
| `review_comment` | 驳回理由（必填）/ 审批意见 |
| `submitted_at` / `reviewed_at` | 时间戳 |

索引：`(workspace_id, status)`（列表）、`(agent_id)`（查重/下钻）、`(submitter_id, status)`（"我的申请"）。

`audit_log` 表 V4 已建（schema 齐全，此前零写入零读取的"死表"），课题⑩由 `AuditFilter` 代码接线激活，无 schema 变更。

## 7. 关键技术决策

| 决策 | 选择 | 理由 | 备选与放弃原因 |
|------|------|------|---------------|
| 审计机制 | Servlet Filter（@Order 3） | 每个写端点自动覆盖、零散点代码、零漏网 | AOP/注解：需逐方法标注，存在漏标风险 |
| 失败请求是否记审计 | 不记 | 边界拦截已有日志；失败写不产生数据变更 | 全量记：噪音大且需区分回滚状态 |
| 审计是否记请求体 | 否，仅 method/path/operatorEmail | 请求体含 API Key / IM 凭证，落库即泄密 | 记全量：合规负资产 |
| 发布直达端点 | 删除（不留后门） | 发布唯一路径 = 审批，双人原则才成立 | 保留并加开关：后门终会被用 |
| 提交人自审 | 一律禁止（含 owner） | 合规产品的正确姿势；单人空间引第二成员即可 | 允许 owner 自审：双人原则形同虚设 |
| 平台级超管 | 不做 | 击穿工作空间租户隔离，是合规负资产 | 测试便利性用第二账号解决（§9） |
| 审批列表可见性 | admin/owner/auditor 全空间，其余仅本人 | 审计角色需全量旁观；开发者无需看他人申请 | 全员可见：泄露他人发布节奏 |
| 错误码段 | 8001-8009 | 1-7xxx 已占，8xxx 为唯一空闲段 | — |

## 8. 明确不做（范围收敛）

多级审批链、部门/小组组织树、通用审批引擎、动态权限配置、平台级超级管理员、审计记录请求体/全资源实体快照、失败写请求审计。企业组织映射为 workspace，权限模型固定五角色。

## 9. 测试章节：第二账号冒烟指南

双人原则意味着单人账号无法走通发布闭环。测试姿势（不新增任何角色）：

1. **注册第二账号**：`POST /api/auth/register`，如 `admin2@agentone.local` / `Admin2#12345`
2. **owner 邀请进空间为 admin**：设置页成员管理 → 添加成员（邮箱 + 角色管理员），或 `POST /api/members {email, role:"admin"}`
3. **切换账号审批**：提交人（owner/developer）提交发布申请 → 登出 → 第二账号登录 → 切到同一工作空间（`POST /api/auth/switch-workspace/{wsId}` 重签 Cookie）→ 发布审批页通过/驳回

curl 冒烟要点：认证是 HttpOnly Cookie（`agentone_token`），用 `-c`/`-b` cookie jar；登录响应体 `token` 恒为 null 是设计。浏览器测试可用隔离上下文（不同身份各自独立 Cookie）。

验收锚点（课题⑩）：提交后编辑/删除被拒（3002/3004）；自审拦截（8003）；空理由驳回（8004）；通过后版本 +1 且 PUBLISHED；observer/auditor 写拦截（2004）且不落审计；审计 Tab 对 owner/admin/auditor 可见、对 developer/observer 隐藏。

## 10. 测试要点

- 单测：`AgentStatusTest`（状态机全分支 + PUBLISH 已删除）、`PublishRequestServiceImplTest`（submit/approve/reject/withdraw/list 角色分流 16 例）、`AuditFilterTest`（写落库 / GET 与跳过路径不落 / 错误标记不落 / mapper 抛异常不阻断请求 8 例）、`WorkspaceRbacFilterTest`（含 auditor 写 → 2004）
- 集成：curl 全链路（§9 验收锚点）+ 浏览器三角色分流（提交人/审批人/审计员）
- 回归：审计是旁路，关闭/失败不影响任何业务链路（best-effort）
