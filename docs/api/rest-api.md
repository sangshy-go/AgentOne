# REST API 参考

> Base URL：`http://localhost:8080`（Docker 部署见 `.env` 的 `API_PORT`）
> 共 18 个 Controller、约 94 个端点。流式对话协议见 [sse-protocol.md](sse-protocol.md)。

## 通用约定

### 统一响应体

```json
{ "code": 0, "message": "success", "data": { } }
```

`code = 0` 表示成功，非 0 为业务错误（见文末错误码表）。列表接口直接返回数组（当前版本无分页）。

### 认证方式

| 适用范围 | 方式 | 凭证位置 |
|----------|------|--------|
| `/api/*`（控制台） | JWT（java-jwt） | HttpOnly Cookie `agentone_token`（浏览器 SPA）；亦接受 `Authorization: Bearer <token>`（API 客户端） |
| `/v1/*`（开放接口） | API Key（SHA-256 哈希存储 + 每日限额） | `X-API-Key: <key>` |
| `/v1/health` | 无需认证 | — |

JWT 中携带 `userId` 与 `workspaceId`，切换工作空间后 Cookie 重新签发。所有业务数据按 `workspace_id` 隔离（MyBatis 租户拦截器自动注入）。

### RBAC 角色（课题⑥/⑩）

角色五值：`owner` / `admin` / `developer` / `observer` / `auditor`（存 `user_workspace.role`；V21 迁移清洗旧 `member` → `developer` 并加 CHECK 约束，V24 扩入 `auditor`）。HTTP 层由 `WorkspaceRbacFilter`（@Order(2)，排 JwtAuthFilter 后）统一强制，**每请求从 DB 取角色，改角色即时生效无需重登录**：

| 规则 | 拦截码 |
|------|--------|
| 非当前工作空间成员访问 `/api/**` | 2002 |
| `observer` / `auditor` 发起写请求（POST/PUT/DELETE/PATCH） | 2004 |
| 非 admin/owner 访问 `/api/members/**` | 2003 |
| 非 owner/admin/auditor 访问 `/api/audit-logs`（Service 层门禁） | 2003 |

白名单跳过检查：`/api/auth/`、`/api/workspaces`、`/v1/*`、`/actuator`、`/error`。"API 用户"不是控制台角色，指 API Key 程序接入（§11/§12）。

`auditor`（审计员，课题⑩新增）：只读角色，**额外**可查审计日志（§18）与全空间审批列表（§17 只读）；不可审批（8007）、不可管成员（2003）、不可写（2004）。角色矩阵详见 `docs/technical/05-auth-rbac.md`。

### 认证 VO（AuthVO）

注册 / 登录 / 切换工作空间成功后，服务端通过 `Set-Cookie` 下发 HttpOnly Cookie（`agentone_token` + refresh token）；**响应体不再携带 token**（`token` / `refreshToken` 字段为 null），仅返回用户与工作空间信息：

```json
{ "token": null, "refreshToken": null, "userId": "...", "email": "...", "nickname": "...", "workspaceId": "...", "workspaceName": "..." }
```

---

## 1. 认证 `/api/auth`

| 方法 | 路径 | 认证 | 请求体 | 说明 |
|------|------|------|--------|------|
| POST | `/api/auth/register` | 无 | `{email*, password*(8-32位), nickname?}` | 注册（邮箱唯一，密码 BCrypt） |
| POST | `/api/auth/login` | 无 | `{email*, password*}` | 登录（5 次失败锁定，Redis 计数） |
| POST | `/api/auth/switch-workspace/{workspaceId}` | JWT | — | 切换工作空间，返回新 Token |

## 2. 工作空间 `/api/workspaces`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/workspaces` | 我的工作空间列表（含角色） |
| POST | `/api/workspaces` | 创建（`{name*, description?}`，创建者自动为 owner） |
| PUT | `/api/workspaces/{id}` | 更新（需权限） |
| DELETE | `/api/workspaces/{id}` | 软删除（需权限） |

## 3. Agent `/api/agents`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents` | 列表（排除已归档） |
| GET | `/api/agents/{id}` | 详情 |
| POST | `/api/agents` | 创建（见 AgentDTO） |
| PUT | `/api/agents/{id}` | **部分更新**（AgentUpdateDTO 全字段可选；仅草稿/测试中可编辑，审批中/已发布拒绝 3002） |
| DELETE | `/api/agents/{id}` | 物理删除 + 级联清理（会话/消息/Skill 绑定/知识库绑定）；审批中拒绝 3004 |
| POST | `/api/agents/{id}/stop` | 停用 |
| POST | `/api/agents/{id}/revert` | 退回草稿 |

> ~~`POST /api/agents/{id}/publish`~~ 已于课题⑩删除：发布不再可直达，唯一路径 = 发布审批（§17）。

**AgentDTO**（创建）：

```json
{
  "name": "必填，≤100 字",
  "description": "≤500 字",
  "category": "分类",
  "icon": "图标标识",
  "avatarUrl": "头像 URL",
  "agentsMd": "AGENTS.md 人格与指令源码（Markdown）",
  "modelConfig": "{\"chatModelId\":\"<model.id>\",\"temperature\":0.7,\"topP\":0.9,\"maxTokens\":2048,\"stream\":true}",
  "modelProviderId": "模型供应商 ID（兼容旧字段）",
  "memoryConfig": "{\"windowSize\":20} 等，JSON 字符串",
  "advancedConfig": "JSON 字符串"
}
```

> `modelConfig` 是 **JSON 字符串**字段。`chatModelId` 指向 `/api/models` 创建的 Chat 模型；为空时回退到全局默认模型（`OPENAI_*` 环境变量）。

**状态机**（课题⑩引入审批）：

```
draft ⇄ testing ──提交发布审批──→ pending_review ──他人通过──→ published ──停用──→ stopped
                                        │ 驳回 / 提交人撤回             （通过时 currentVersion+1）
                                        └──→ draft
```

- `pending_review`（审批中）冻结编辑/删除/停用（保证「审什么 = 发什么」），但**控制台对话仍可用**（审批前继续验证）；IM 回调维持仅转发 `published`
- `revert` 可从 testing/stopped 退回 draft；仅 draft / testing / pending_review / published 可对话
- 提交/通过/驳回/撤回端点见 §17

## 4. 对话 `/api/chat`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步对话 → `{sessionId, reply, tokenCount, durationMs, traceId}` |
| POST | `/api/chat/stream` | 流式对话（SSE，见 sse-protocol.md） |
| GET | `/api/chat/sessions?agentId=` | 会话列表（仅当前用户自己的会话，不含 IM 回调等虚拟用户会话） |
| GET | `/api/chat/sessions/{sessionId}` | 会话详情 |
| GET | `/api/chat/sessions/{sessionId}/messages` | 会话消息列表 |
| PUT | `/api/chat/sessions/{sessionId}/rename` | 重命名（body：`{title*}`，≤100 字） |
| DELETE | `/api/chat/sessions/{sessionId}` | 删除会话（连同消息） |
| POST | `/api/chat/attachments` | 上传附件（multipart `file`）→ `{id,kind,fileName,mimeType,fileSize}` |
| GET | `/api/chat/attachments/{id}` | 附件字节流（Content-Disposition: inline） |

**ChatRequestDTO**：`{agentId*, message?, attachmentIds?, sessionId?, stream?}`
`message` 与 `attachmentIds` 至少其一。

**附件**：
- 图片：png/jpg/jpeg/webp/gif ≤10MB；文档：pdf/docx/txt/md/csv ≤20MB
- 后端 Tika 嗅探 MIME（防扩展名伪装），拒绝 `text/html`、`application/xhtml+xml`、`image/svg+xml`
- 文档即时解析入库 `parsed_text`（≤200k 字符）；发送时按 `agentone.chat.attachment-max-tokens`（默认 6000）截断后注入 prompt
- 图片走 `ImageBlock(Base64Source)` 多模态通道，需 Agent 绑定视觉模型（如 qwen-vl 系列）
- API Key 调用（/v1/chat）不支持附件，返回 4015
- 删除会话时级联删除所属附件

## 5. 模型供应商 `/api/model-providers`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/model-providers` | 创建 `{name*, provider*, apiKey*, baseUrl*}`（OpenAI 兼容协议） |
| GET | `/api/model-providers` | 列表 |
| GET | `/api/model-providers/{id}` | 详情 |
| PUT | `/api/model-providers/{id}` | 更新（字段可选） |
| DELETE | `/api/model-providers/{id}` | 删除 |
| POST | `/api/model-providers/{id}/check` | 连通性检测 → `{available, message, latencyMs}` |

## 6. 模型 `/api/models`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/models/providers/{providerId}/models` | 创建模型 `{modelType*, modelId*, displayName?, contextSize=4096, maxTokens=2048, dimensions?}` |
| GET | `/api/models/providers/{providerId}/models` | 供应商下模型列表 |
| GET | `/api/models/{modelId}` | 模型详情（此处 modelId 为记录 ID） |
| PUT | `/api/models/{modelId}` | 更新 |
| DELETE | `/api/models/{modelId}` | 删除 |
| GET | `/api/models/by-type/{modelType}` | 按类型列表（`chat` / `embedding` / `rerank` / `image2text`） |

> `modelId`（请求体字段）是调用 LLM API 时的模型名，如 `qwen-plus`、`text-embedding-v3`；Embedding 维度首次使用时自动探测。

## 7. 模型快速检测 `/api/model-check`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/model-check/check` | 不落库的快速连通性检测（ModelCheckDTO） |

## 8. 知识库 `/api/knowledge`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/bases` | 创建知识库（见下） |
| GET | `/api/knowledge/bases` | 列表 |
| GET | `/api/knowledge/bases/{id}` | 详情 |
| PUT | `/api/knowledge/bases/{id}` | 更新（名称/描述/分块配置） |
| DELETE | `/api/knowledge/bases/{id}` | 删除（连同文档/分块/向量） |
| POST | `/api/knowledge/bases/{knowledgeId}/documents` | 上传文档（`multipart/form-data`，字段名 `file`，≤50MB） |
| GET | `/api/knowledge/bases/{knowledgeId}/documents` | 文档列表（含 status：pending/processing/ready/error 与 errorMsg） |
| DELETE | `/api/knowledge/documents/{documentId}` | 删除文档 |
| POST | `/api/knowledge/documents/{documentId}/retry` | 重试失败文档（复用已存原文，不重新解析；解析层失败需删除重传） |
| POST | `/api/knowledge/bases/{knowledgeId}/search?query=&topK=5` | 向量检索 Top-K |
| POST | `/api/knowledge/bindings` | Agent 绑定知识库 `{agentId*, knowledgeId*, topK=5, similarityThreshold=0.7}` |
| PUT | `/api/knowledge/bindings/{bindingId}` | 更新绑定参数 |
| DELETE | `/api/knowledge/bindings/{bindingId}` | 解绑 |
| GET | `/api/knowledge/agents/{agentId}/bindings` | Agent 的绑定列表 |
| GET | `/api/knowledge/bases/{knowledgeId}/bindings` | 知识库的绑定列表 |

**KnowledgeBaseDTO**：`{name*, description?, embeddingModel?, embeddingModelId?, chunkStrategy?(by-length/by-title/by-paragraph，默认 by-length), chunkSize?(token 计，默认 400), chunkOverlap?(字符，默认 60)}`

支持文件类型：`.pdf` `.docx` `.doc` `.md` `.txt` `.csv` `.html`（后端白名单强校验）。

## 9. Skill `/api/skills`（Cookie）

Skill 列表为**合并视图**：内置（builtin，虚拟挂载）+ 用户 Skill（api / prompt，落 `skill` 表）+ MCP 工具（`mcp-{serverId}-{toolName}` 虚拟 ID，见 §10）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skills?page&size&keyword&category&type` | 工作空间 Skill 分页列表（**管理视角**，builtin/api/prompt/mcp 合并，含 type/source/category）；keyword 名称/描述大小写不敏感，category/type 精确筛选 |
| GET | `/api/skills/plaza?q&cat` | 技能广场（**发现视角**，Skill 中心 v2）：本空间 active 用户 Skill + builtin + **已发布**的 MCP 工具；q 搜索、cat 按工种过滤；不分页 |
| POST | `/api/skills` | 创建用户 Skill（SkillDTO：`{name*, type?, category?, description?, inputSchema?, outputSchema?, config*, version?}`；type 缺省 `api`，可选 `prompt`；category 缺省"其他"。api：config 须含 `url` 过 SSRF 校验；prompt：config 须含非空 `content`（`{"content":"..."}`），无出站请求，inputSchema/outputSchema 强制 `{}`。config 可带 `actionType:true` 标记动作型） |
| PUT | `/api/skills/{skillId}` | 更新用户 Skill（内置不可改 5006；禁止 type 变更 5007） |
| DELETE | `/api/skills/{skillId}` | 删除（仍被 Agent 绑定拒绝，5005） |
| POST | `/api/skills/{skillId}/test` | 测试调用 `{params:{...}}` → 真实执行结果（builtin 不支持，5006）；prompt 类型返回 `data:{name, content}` |
| GET | `/api/skills/{skillId}/export` | 导出 Skill 定义 JSON（format=agentone-skill，含全字段 + exportedAt；仅用户 Skill，builtin/mcp 拒绝 5006） |
| POST | `/api/skills/import` | 导入 Skill 定义（body 为导出 JSON；外来 format 5007；同名+同 type 已存在 5013；成功 source=imported，走与创建相同的校验链路） |
| POST | `/api/skills/import-package` | 导入技能包（multipart，Skill 中心 v2）：`file`=单个 `.zip` 或单个 `.md`；`files[]`+`paths[]`=整个文件夹（两者数量须一致）。以 `SKILL.md` 为入口，frontmatter 带出 name/description/category；落 `skill` + `skill_package_file`（脚本仅存储不执行）；同名 5013，包非法 5014 |
| GET | `/api/skills/{skillId}/package-files` | 技能包文件树（详情抽屉展示 SKILL.md + scripts + resources）→ `[{path, kind(script/resource/doc), size, content?}]` |
| PUT | `/api/skills/{skillId}/status?enabled=` | 启用/停用（我的技能 toggle）：用户 Skill 切换 active/disabled；MCP 工具等价切换发布状态；内置技能系统托管拒绝（5006） |
| POST | `/api/skills/{skillId}/invoke` | 广场「试一试」`{params?, confirmToken?}`。**动作型两阶段**：首次（无 token）返回 `confirmRequired=true + confirmToken + draftParams`，不执行；带有效 token 二次调用才真实执行。非动作型直接执行。写审计（agentId=plaza）；令牌无效/过期 5015 |
| GET | `/api/skills/debug/targets` | 调试器第 1 步：可调试 Skill 列表（含 inputSchema） |
| POST | `/api/skills/debug/preview` | 调试器第 2 步：参数预检 + 执行计划 `{skillId*, params}` → `{valid, errors, plan}`（不发起真实调用） |
| POST | `/api/skills/debug/run` | 调试器第 3 步：真实执行 `{skillId*, params, sessionId?}` → `{success, data, errorMessage, durationMs, traceId, sessionId}`（写审计，agentId=debugger） |
| GET | `/api/skills/bindings/{agentId}` | Agent 的 Skill 绑定 |
| POST | `/api/skills/bind` | 绑定 `{agentId, skillId, config?}`（skillId 可为 builtin/api/prompt/mcp 任意形态；未发布的 MCP 工具拒绝 5016） |
| DELETE | `/api/skills/bindings/{bindingId}` | 解绑 |
| PUT | `/api/skills/bindings/{bindingId}/toggle?enabled=` | 启用/停用 |

## 10. MCP Server `/api/mcp-servers`（Cookie）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/mcp-servers` | 创建（McpServerDTO：`{name*, transport*(stdio/sse/streamable_http), url?, command?, args?[], headers?{}, timeoutMs?, status?}`；stdio 需 command，http 类需 url） |
| PUT | `/api/mcp-servers/{id}` | 更新（transport/url/command 变化会关闭旧连接，需重新 connect） |
| DELETE | `/api/mcp-servers/{id}` | 删除（工具仍被 Agent 绑定拒绝，5012） |
| GET | `/api/mcp-servers?page&size` | 分页列表（含 `connected` / `toolCount`） |
| GET | `/api/mcp-servers/{id}` | 详情 |
| POST | `/api/mcp-servers/{id}/connect` | 连接并发现工具 → `List<McpToolVO>`（工具注册为虚拟 Skill） |
| POST | `/api/mcp-servers/{id}/disconnect` | 断开（保留配置与 Server 行） |
| GET | `/api/mcp-servers/{id}/tools` | 已发现的工具列表（skillId / toolName / description / inputSchema / `published` / `actionType`） |
| PUT | `/api/mcp-servers/{id}/tools/{toolName}/publish` | 工具级「发布到广场」开关（Skill 中心 v2 治理，body `{published}`；默认关闭，IT 显式发布后广场才可见/可绑定） |

> MCP 的 url/command 不做 SSRF 拦截（stdio 即本地命令执行能力，管理员配置行为），信任边界与 API Skill 不同，详见 docs/technical/06-mcp-integration.md §6。

## 11. API Key `/api/api-keys`（Cookie）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/api-keys` | 创建 `{env*(live/test), allowedAgents?[], dailyLimit?=1000}` → **仅此时返回明文 Key** |
| GET | `/api/api-keys` | 当前工作空间 Key 列表（脱敏） |
| DELETE | `/api/api-keys/{id}` | 停用 |

## 12. 开放接口 `/v1`（X-API-Key）

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/v1/chat` | X-API-Key | 同步对话（同 ChatRequestDTO，校验 Key 的 allowedAgents 与每日限额） |
| POST | `/v1/chat/stream` | X-API-Key | 流式对话（SSE） |
| GET | `/v1/health` | 无 | 健康检查 → `data: {status:"UP", database:{status,type}, redis:{status,type}}`（同统一 Result 包装） |

## 13. 成员管理 `/api/members`（Cookie，仅 admin/owner）

管理员门禁由 `WorkspaceRbacFilter` 统一拦截（非 admin/owner → 2003），Controller 不重复校验。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/members?current&size` | 成员分页列表 → `{userId, email, nickname, role, joinedAt}` |
| POST | `/api/members` | 添加成员 `{email*, role*}`；role 限 `admin`/`developer`/`observer`/`auditor`（owner 不可授予）；按邮箱添加**已注册**用户，无邀请流程 |
| PUT | `/api/members/{userId}` | 变更角色 `{role*}`；禁止改 owner 行 |
| DELETE | `/api/members/{userId}` | 移除成员；禁止移除 owner、禁止移除自己 |

## 14. 监控 `/api/monitor`（Cookie）

数据底座零新埋点：复用对话链路已写入的 `chat_session` / `chat_message` / `skill_call_log`（session_id 串联）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/monitor/sessions?agentId&keyword&current&size` | 全工作空间会话分页 → `{id, title, agentId, agentName, userEmail, messageCount, tokenCount, createdAt, updatedAt}` |
| GET | `/api/monitor/sessions/{id}/timeline` | 会话时间线 → `{session, items[]}`；items = 消息 + Skill 调用按 createdAt 归并，`kind=message`（含 role/content）或 `kind=skill_call`（含 skillName/status/errorMessage/durationMs）。**chat_message 无租户列**：先查会话验归属再查消息（4004 会话不存在） |
| GET | `/api/monitor/skill-calls?agentId&skillId&status&current&size` | Skill 调用记录分页 → `{id, agentId, agentName, skillId, skillName, sessionId, status, errorMessage, durationMs, tokenCount, createdAt}` |

## 15. 仪表盘 `/api/dashboard`（Cookie）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard/stats` | `{agentCount, knowledgeCount, todayChatCount, activeUsers7d, dailyChats:[{statDate, statCount}×7 缺日补0升序], recentAgents:[{id,name,category,updatedAt}×5]}` |

## 16. IM 机器人 `/api/im`（课题⑤）

管理端走 Cookie 认证；凭证 AES-256-GCM 加密落库（密钥来自环境变量 `AGENTONE_IM_SECRET_KEY`，64 位 hex），列表只回 `configMasked` 掩码，任何接口不回传原始凭证。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/im/bots` | 机器人列表 → `[{id, name, platform, mode, agentId, status, createdBy, createdAt, configMasked}]` |
| POST | `/api/im/bots` | 创建。body：`{name, platform: dingtalk\|wecom, mode: webhook\|callback, agentId?, config{}}`。config 键：钉钉 webhook = `webhookUrl`（强制 `https://oapi.dingtalk.com/` 域名，防 SSRF）+ `secret?`；钉钉 callback = `appSecret`；企微 callback = `corpId/agentId/secret/token/encodingAesKey`（创建时即校验 encodingAesKey 合法性）。企微无 webhook 形态，wecom+webhook 拒绝。**创建后默认 `disabled`**，测试发送验证凭证后由 PUT 启用 |
| PUT | `/api/im/bots/{id}` | 更新。body 全可选：`{name?, agentId?, status?: active\|disabled, config?}` |
| DELETE | `/api/im/bots/{id}` | 删除（发送者会话映射由 FK CASCADE 清理） |
| POST | `/api/im/bots/{id}/send` | 主动发送测试消息。body：`{text, msgType?: text\|markdown, title?}`。仅钉钉 webhook 模式支持，其余 5206。不受启停状态限制（测试发送在启用前进行） |

### 平台回调 `/api/im/callback`（公开端点，无 JWT，由平台签名鉴权）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/im/callback/wecom/{botId}` | 企微回调 URL 验证：校验 `msg_signature` 后解密 `echostr` 明文回显 |
| POST | `/api/im/callback/wecom/{botId}` | 企微消息回调：验签 → 解密 → 仅处理文本 → 被动回复（加密 XML 信封） |
| POST | `/api/im/callback/dingtalk/{botId}` | 钉钉企业机器人回调：`timestamp`+`sign` 头验签（1h 防重放窗口）→ 响应体回复 `{msgtype, text:{content}}` |

回调行为约定（防平台重试风暴）：机器人不存在/停用/非 callback 模式、报文非法、body 缺失时一律静默返回（企微回 `success`，钉钉回 `{}`）；仅绑定**已发布** Agent 的机器人才会转发对话，虚拟用户身份为 `im:{platform}:{senderId}`，发送者→会话映射存 `im_sender_session` 实现多轮记忆。

## 17. 发布审批 `/api/publish-requests`（课题⑩，Cookie）

Agent 发布唯一路径：提交申请 → 他人审批（双人原则，提交人不可自审，含 owner）。`submit` 需写权限角色（developer/admin/owner，observer/auditor 被 2004 拦截）；`approve`/`reject` 需 admin/owner（8007）；列表按角色分流（admin/owner/auditor 见全空间，其余仅见本人提交）。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/publish-requests` | 提交发布审批 `{agentId*}`。Agent 须 draft/testing（8005）且无待审申请（8002）；成功后 Agent 转 `pending_review`，配置快照入 `config_snapshot` |
| GET | `/api/publish-requests?status=&agentId=&page=&size=` | 审批单分页列表 → `PublishRequestVO`（含 agentName 快照、agentStatus 实时状态、提交/审核人与邮箱、驳回意见） |
| POST | `/api/publish-requests/{id}/approve` | 通过：双人校验（8003）→ Agent 转 `published` 且 `currentVersion+1`；Agent 已删/归档 → 8009 |
| POST | `/api/publish-requests/{id}/reject` | 驳回 `{comment*}`（理由必填 8004）：双人校验（8003）→ Agent 回 `draft` |
| POST | `/api/publish-requests/{id}/withdraw` | 撤回（仅提交人本人 8008，仅待审单）→ Agent 回 `draft` |

`PublishRequestVO`：`{id, agentId, agentName, agentStatus, status(pending/approved/rejected/withdrawn), submitterId, submitterEmail, reviewerId, reviewerEmail, reviewComment, submittedAt, reviewedAt}`（无 workspaceId，数据范围由租户拦截器保证）。

## 18. 审计日志 `/api/audit-logs`（课题⑩，Cookie）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/audit-logs?action=&resourceType=&keyword=&page=&size=` | 审计日志分页（门禁：仅 owner/admin/auditor，否则 2003；范围限当前工作空间）。`keyword` 模糊匹配操作人 ID / 资源 ID → `{id, operatorId, operatorEmail, action, resourceType, resourceId, method, path, createdAt}` |

**审计写入机制**（`AuditFilter` @Order(3)，排 JwtAuth/Rbac 之内层）：所有**成功**的写请求（POST/PUT/DELETE/PATCH）自动落 `audit_log`；action/resource_type/resource_id 从路径推导，`detail` 仅记 `{method, path, operatorEmail}`——**不记请求体**（模型 API Key / IM 凭证等密钥走写请求体，落库即泄密）。跳过路径：`/api/auth/`、`/api/workspaces`、`/v1/`、`/api/chat`（对话运行时已有全量留痕）、`/api/im/callback/`（虚拟用户）。业务失败请求不记（边界拦截已有各自日志）；审计写入 best-effort，失败仅 warn 不阻断业务。

---

## 错误码

| 码 | 含义 |
|----|------|
| 0 | 成功 |
| 400 | 请求参数错误（校验失败，message 含具体字段信息） |
| 401 | 未认证 / API Key 无效 |
| 403 | 无权限 / API Key 无权调用此 Agent |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 邮箱已注册 |
| 1002 | 邮箱或密码错误 |
| 1003 | 账号已锁定（登录失败超限） |
| 1004 | Token 无效或已过期 |
| 1005 | 用户不存在 |
| 2001 | 工作空间不存在 / API Key 不存在 |
| 2002 | 无权访问该工作空间（非成员） |
| 2003 | 仅 admin/owner 可操作（成员管理 / 修改或删除工作空间） |
| 2004 | observer / auditor 角色为只读，不允许写操作 |
| 2005 | 无效的角色（可授予：admin / developer / observer / auditor） |
| 2006 | 邮箱未注册（添加成员时） |
| 2007 | 该用户已是工作空间成员 |
| 2008 | 不能变更所有者角色 / 不能移除所有者 |
| 2009 | 不能移除自己 |
| 2010 | 该用户不是工作空间成员 |
| 3001 | Agent 不存在 |
| 3002 | 当前状态不可编辑：已发布需先退回草稿；审批中需先撤回申请（审什么 = 发什么） |
| 3003 | Agent 已停用，无法对话 |
| 3004 | 当前状态不允许该操作（状态机约束；含审批中禁止删除/停用/重复提交） |
| 4002 | 会话不存在 |
| 4003 | 无权访问该会话 |
| 4004 | 会话不存在（监控时间线，含跨租户拦截） |
| 4010 | 附件不存在 |
| 4011 | 无权访问该附件（跨工作空间/用户） |
| 4012 | 文件类型不允许（扩展名或 MIME 嗅探拒绝） |
| 4013 | 文件超过大小限制 |
| 4014 | 文档解析失败 |
| 4015 | API Key 调用不支持附件，请使用 /api 路径 |
| 5001 | Agent 已绑定此 Skill |
| 5002 | Skill 不存在（含不可调试） |
| 5003 | Skill 绑定记录不存在 |
| 5004 | 无权操作其他工作空间的 Skill（绑定 / 测试 / 调试） |
| 5005 | 该 Skill 仍被 Agent 绑定，请先解除绑定（删除保护） |
| 5006 | 该 Skill 不支持直接测试 / 内置 Skill 不可修改 / 不可导出 |
| 5007 | Skill 配置非法（不是合法 JSON / api 缺 url / prompt 缺 content / 未知 type / 变更 type / 导入外来格式） |
| 5008 | Skill 地址不安全（SSRF 拦截） |
| 5009 | MCP Server 不存在（含跨空间访问） |
| 5010 | MCP 配置非法（transport 枚举 / 缺 command / 缺 url） |
| 5011 | MCP 连接失败 / 工具发现失败（携带根因消息） |
| 5012 | MCP Server 的工具仍被 Agent 绑定，拒绝删除 |
| 5013 | 导入 Skill 时同空间已存在同名+同 type 的 Skill，请先删除或改名 |
| 5014 | 技能包非法（缺 SKILL.md / frontmatter 无 name / zip 解析失败 / 路径非法 zip-slip / 超大小或数量上限 / files 与 paths 数量不一致） |
| 5015 | 动作型技能确认令牌无效或已过期（请重新生成草稿并确认） |
| 5016 | 该 MCP 工具未发布到广场，请先在 MCP 管理中发布（绑定拦截） |
| 5200 | 未配置 IM 加密密钥（设置环境变量 AGENTONE_IM_SECRET_KEY 后重启） |
| 5201 | IM 配置密文损坏或与当前密钥不匹配 |
| 5202 | IM 机器人参数非法（平台/模式组合、缺配置项、webhookUrl 非官方域名、机器人不存在/已停用） |
| 5203 | 钉钉发送失败（携带平台 errcode/errmsg） |
| 5204 | 企微 encodingAesKey 非法（应为 43 位 Base64，解码后 32 字节） |
| 5205 | 企微回调签名校验失败 / receiveId 不匹配 / 解密失败 |
| 5206 | 当前机器人不支持主动发送（仅钉钉自定义机器人 webhook 支持） |
| 6001 | 知识库不存在 / 模型供应商不存在 |
| 6002 | 模型不存在 / Chat 模型不存在 |
| 6003 | 文档不存在 |
| 6004 | Agent 已绑定此知识库 |
| 6005 | 只能重试处理失败的文档 |
| 6006 | 原文内容为空（解析失败），请重新上传 |
| 6007 | 绑定关系不存在 |
| 6008 | Embedding 模型配置缺失 |
| 6009 | 不支持的文件类型 |
| 6010 | 重复文档（同名同大小） |
| 6011 | Chat 模型关联的服务商配置不存在 |
| 6012 | 当前工作空间无权使用该模型供应商（跨租户模型保护） |
| 6013 | 无权操作该模型 / 在该供应商下创建模型（跨工作空间模型保护） |
| 6014 | 文档正在处理中，请等待处理完成后再删除 |
| 6015 | Embedding 模型不可用（创建/修改时维度探测失败，携带根因，请核对模型 ID 与 Base URL） |
| 6016 | Embedding 模型已被知识库绑定，不允许修改模型 ID/类型或删除 |
| 6017 | 供应商下仍有被知识库绑定的 Embedding 模型，不允许删除供应商（防止连级删除产生死引用） |
| 6018 | Agent 未配置 Chat 模型且系统无全局默认模型（对话入口 fail-fast；此前静默回退占位配置，报晦涩网络错误） |
| 7001–7005 | 模型供应商参数校验（不存在 / 名称 / 类型 / Key / URL 缺失） |
| 8001 | 发布申请不存在 / 已处理（非待审状态） |
| 8002 | 该 Agent 已有待审的发布申请，请勿重复提交 |
| 8003 | 双人原则：提交人不能审批自己的发布申请 |
| 8004 | 驳回必须填写理由 |
| 8005 | 仅草稿/测试中状态的 Agent 可提交发布审批 |
| 8006 | 当前角色无权提交发布审批（只读角色） |
| 8007 | 仅管理员或所有者可审批发布申请 |
| 8008 | 仅提交人本人可撤回发布申请 |
| 8009 | Agent 已被删除或归档，该发布申请已失效 |
