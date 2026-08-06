# REST API 参考

> Base URL：`http://localhost:8080`（Docker 部署见 `.env` 的 `API_PORT`）
> 共 12 个 Controller、约 65 个端点。流式对话协议见 [sse-protocol.md](sse-protocol.md)。

## 通用约定

### 统一响应体

```json
{ "code": 0, "message": "success", "data": { } }
```

`code = 0` 表示成功，非 0 为业务错误（见文末错误码表）。列表接口直接返回数组（当前版本无分页）。

### 认证方式

| 适用范围 | 方式 | 请求头 |
|----------|------|--------|
| `/api/*`（控制台） | JWT（Sa-Token） | `Authorization: Bearer <token>` |
| `/v1/*`（开放接口） | API Key（SHA-256 哈希存储 + 每日限额） | `X-API-Key: <key>` |
| `/v1/health` | 无需认证 | — |

JWT 中携带 `userId` 与 `workspaceId`，切换工作空间后需使用新返回的 Token。所有业务数据按 `workspace_id` 隔离（MyBatis 租户拦截器自动注入）。

### 认证 VO（AuthVO）

注册 / 登录 / 切换工作空间均返回：

```json
{ "token": "...", "userId": "...", "email": "...", "nickname": "...", "workspaceId": "...", "workspaceName": "..." }
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
| PUT | `/api/agents/{id}` | **部分更新**（AgentUpdateDTO 全字段可选；已发布需先退回草稿） |
| DELETE | `/api/agents/{id}` | 物理删除 + 级联清理（会话/消息/Skill 绑定/知识库绑定） |
| POST | `/api/agents/{id}/publish` | 发布 |
| POST | `/api/agents/{id}/stop` | 停用 |
| POST | `/api/agents/{id}/revert` | 退回草稿 |

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

**状态机**：`draft → testing → published → stopped`；`revert` 可从 testing/published/stopped 退回 draft。仅 draft/testing/published 可对话。

## 4. 对话 `/api/chat`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步对话 → `{sessionId, reply, tokenCount, durationMs, traceId}` |
| POST | `/api/chat/stream` | 流式对话（SSE，见 sse-protocol.md） |
| GET | `/api/chat/sessions?agentId=` | 会话列表 |
| GET | `/api/chat/sessions/{sessionId}` | 会话详情 |
| GET | `/api/chat/sessions/{sessionId}/messages` | 会话消息列表 |
| PUT | `/api/chat/sessions/{sessionId}/rename` | 重命名（body：`{title*}`，≤100 字） |
| DELETE | `/api/chat/sessions/{sessionId}` | 删除会话（连同消息） |

**ChatRequestDTO**：`{agentId*, message*, sessionId?, stream?}`

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

## 9. Skill `/api/skills`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skills` | 内置 Skill 列表（知识库检索 / HTTP 请求 / 代码执行） |
| GET | `/api/skills/bindings/{agentId}` | Agent 的 Skill 绑定 |
| POST | `/api/skills/bind` | 绑定 `{agentId, skillId, config?}` |
| DELETE | `/api/skills/bindings/{bindingId}` | 解绑 |
| PUT | `/api/skills/bindings/{bindingId}/toggle?enabled=` | 启用/停用 |

## 10. API Key `/api/api-keys`（JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/api-keys` | 创建 `{env*(live/test), allowedAgents?[], dailyLimit?=1000}` → **仅此时返回明文 Key** |
| GET | `/api/api-keys` | 当前工作空间 Key 列表（脱敏） |
| DELETE | `/api/api-keys/{id}` | 停用 |

## 11. 开放接口 `/v1`（X-API-Key）

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/v1/chat` | X-API-Key | 同步对话（同 ChatRequestDTO，校验 Key 的 allowedAgents 与每日限额） |
| POST | `/v1/chat/stream` | X-API-Key | 流式对话（SSE） |
| GET | `/v1/health` | 无 | 健康检查 → `data: {status:"UP", database:{status,type}, redis:{status,type}}`（同统一 Result 包装） |

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
| 2002 | 无权访问该工作空间 |
| 3001 | Agent 不存在 |
| 3002 | 已发布的 Agent 不能直接修改，请先退回草稿 |
| 3003 | Agent 已停用，无法对话 |
| 3004 | 当前状态不允许该操作（状态机约束） |
| 4002 | 会话不存在 |
| 4003 | 无权访问该会话 |
| 5000–5003 | Skill 绑定相关（序列化失败 / 重复绑定 / Skill 不存在 / 绑定记录不存在） |
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
| 7001–7005 | 模型供应商参数校验（不存在 / 名称 / 类型 / Key / URL 缺失） |
