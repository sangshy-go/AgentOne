# 系统架构技术方案

## 1. 概述

AgentOne 是开源 AI Agent 中台，MVP 阶段跑通「配模型 → 建知识库 → 创建 Agent → 对话 → API 接入」完整闭环。

**技术栈**：

| 层 | 选型 | 版本 |
|----|------|------|
| 后端框架 | Spring Boot | 3.4.0 |
| Agent 引擎 | AgentScope Java 2.0（`agentscope-harness`，MVP 使用 ReActAgent 内核） | 2.0.0 |
| RAG | Spring AI（Workflow 管道：分块/向量化/检索）+ PgVector | 1.0.0 |
| ORM | MyBatis-Plus（含租户拦截器） | 3.5.x |
| 认证 | Sa-Token（JWT Simple 模式） | — |
| 数据库迁移 | Flyway（V1–V21） | — |
| 前端 | Vue 3 + Vite + Naive UI + Pinia | 3.5 / 6 / 2.40 |
| 基础设施 | PostgreSQL 16 (pgvector) + Redis 7 + MinIO（预留，MVP 未启用） | — |
| 语言/运行 | Java 17 / Node 20 | — |

## 2. 模块划分（Maven 多模块）

```
agentone-server/
├── agentone-api        # 启动模块：AgentOneApplication、Flyway 迁移、全局配置
├── agentone-auth       # 认证：注册/登录/JWT/登录失败限制（Redis 计数）
├── agentone-workspace  # 工作空间：多租户主体、成员角色
├── agentone-agent      # Agent 引擎：CRUD/状态机、ReAct 对话（AgentScope ReActAgent）、SSE、模型供应商、AGENTS.md 解析、记忆（依赖 agentscope-harness，Phase 2 渐进采纳 Harness 能力）
├── agentone-knowledge  # 知识库 RAG：解析/分块/向量化/检索/绑定 + 模型（model 表）管理
├── agentone-skill      # Skill 框架：SkillExecutor 接口 + 内置 HTTP/代码执行 Skill
├── agentone-apikey     # 开放 API：API Key 管理、/v1/chat、健康检查
└── agentone-common     # 通用：Result/ResultCode、BusinessException、RuntimeContext、租户拦截器、TokenCounter
```

依赖方向（单向）：`api → auth/workspace/agent/knowledge/skill/apikey → common`。
跨模块调用走 Service 接口（如 ChatService 依赖 KnowledgeService、AgentSkillService），不跨层直调 Mapper。

## 3. 分层架构

```
Controller（参数校验 @Valid + 请求转发，不写业务逻辑）
    ↓
Service（业务逻辑 + 事务 @Transactional(rollbackFor=Exception.class)，标注在 Service 层）
    ↓
Mapper（MyBatis-Plus BaseMapper，数据访问）
    ↓
PostgreSQL / Redis / 外部 LLM API
```

横切组件（`agentone-common`）：

| 组件 | 职责 |
|------|------|
| `JwtAuthFilter` | `/api/*` JWT 认证，解析 userId/workspaceId/email 写入 `RuntimeContext`（ThreadLocal） |
| `WorkspaceRbacFilter` | 工作空间角色强制（课题⑥，@Order 排 JwtAuthFilter 后）：每请求从 DB 取角色写 `Context.role`，非成员 2002 / observer 写操作 2004 / `/api/members` 限 admin+owner 2003；角色变更即时生效 |
| `ApiKeyAuthFilter` | `/v1/*` X-API-Key 认证 + 每日限额（`/v1/health` 白名单） |
| `WorkspaceInterceptor` | MyBatis 租户拦截器：SQL 自动注入 `workspace_id` 条件与填充（document/document_chunk/vector_store 等在白名单，靠 knowledgeId 间接隔离） |
| `GlobalExceptionHandler` | 统一异常 → `Result`（业务异常携带错误码） |
| `Result<T>` | 统一响应 `{code, message, data}`，code=0 成功 |

## 4. 请求数据流

### 4.1 控制台请求（浏览器）

```
浏览器 → Nginx（try_files SPA / 反代 /api、/v1，SSE 关闭缓冲）
  → JwtAuthFilter（JWT → RuntimeContext）
  → WorkspaceRbacFilter（DB 取角色 → 2002/2004/2003 或放行）
  → Controller（@Valid 校验）
  → Service（业务 + 租户隔离由拦截器自动完成）
  → Mapper → PostgreSQL
```

### 4.2 对话请求（核心链路）

```
POST /api/chat/stream
  → ChatServiceImpl.chatStream
      1. loadAgent（状态校验：draft/testing/published 可对话）
      2. getOrCreateSession
      3. saveMessage(user)            ← 先入库，RAG 以当前消息为 query
      4. buildSystemPrompt
           = AGENTS.md（VariableInjector 变量注入）
           + buildSkillPrompt（绑定 Skill 的描述文本）
           + buildRagContext（多知识库检索 → 阈值过滤 → 全局排序 → 3000 token 截断）
           + memoryService.buildContextPrompt（滑动窗口历史）
      5. buildReActAgent（chatModelId → model 表 → provider 表解析 apiKey/baseUrl，
                          否则回退全局 agentscope.openai 配置；enable_thinking=true）
      6. agent.streamEvents() → 映射 TextBlockDeltaEvent→delta / ThinkingBlockDeltaEvent→thinking
      7. SSE: session → delta/thinking... → done（失败转 error 事件）
      8. doOnComplete 保存完整回复（临时恢复 RuntimeContext 供租户填充）
```

### 4.3 开放 API（程序接入）

```
POST /v1/chat（X-API-Key）
  → ApiKeyAuthFilter（SHA-256 哈希比对 + 每日限额 + 状态校验）
  → V1ChatController（校验 Key 的 allowedAgents 白名单）
  → 复用 ChatService（与 /api/chat 同一实现）
```

## 5. 数据模型（20 张表，Flyway 管理）

| 域 | 表 |
|----|-----|
| 账户与租户 | `sys_user`、`workspace`、`user_workspace`（角色） |
| Agent | `agent`（6 维配置存 JSONB/文本）、`chat_session`、`chat_message` |
| 知识库 | `knowledge_base`、`document`（含 raw_content 原文）、`document_chunk`、`vector_store_{dim}`（Spring AI 按维度建表） |
| 模型 | `model_provider`（供应商）、`model`（chat/embedding/rerank，两层结构） |
| Skill | `skill`、`agent_skill_binding`、`agent_knowledge_binding` |
| 开放 API | `api_key`（哈希存储）、`chat_api_log` |
| Phase 2 预留 | `mcp_server`、`scheduled_task`、`task_execution_log`、`audit_log`、`skill_call_log`（对话链路写入，课题⑥监控时间线/Skill 调用查询读取） |

- 主键：32 位 hex 字符串（MyBatis-Plus IdType），`vector_store` 的 id 列为 TEXT（V6 修复，与 Spring AI 默认 UUID 兼容问题）。
- 多租户：业务表带 `workspace_id`，由 `WorkspaceInterceptor` 自动过滤。

## 6. 部署拓扑（Docker Compose 全栈）

```
                    ┌────────────────┐
 浏览器 ──:80─────▶ │ frontend(nginx)│ 静态资源 + 反代 + SSE
                    └───────┬────────┘
                            │ /api、/v1
                    ┌───────▼────────┐
 外部 API ─:8080──▶ │ backend(Spring)│ Flyway 自动建表，健康检查 /v1/health
                    └──┬─────┬───────┘
              ┌────────▼┐ ┌──▼──────┐        ┌──────────────┐
              │postgres │ │ redis   │        │ 外部 LLM API │
              │+pgvector│ │ 会话/限流│        │ (OpenAI 兼容)│
              └─────────┘ └─────────┘        └──────────────┘
```

- 根目录 `docker-compose.yml` 编排 4 个服务（postgres / redis / backend / frontend）。**MinIO 为 Phase 2+ 对象存储预留**，MVP 文档原文存 `document.raw_content`、未走对象存储，故一键部署暂不包含（本地开发编排 `agentone-server/docker/docker-compose.yml` 仍含 minio）。
- 后端配置注入：`docker-compose.yml` 通过 Spring Boot 标准环境变量（`SPRING_DATASOURCE_URL` 等）覆盖 `application.yml` 默认值，**零代码改动**；JDBC URL 保留 `stringtype=unspecified`（JSONB 绑定依赖）。
- 本地开发：`agentone-server/start.sh` + `agentone-server/docker/docker-compose.yml`（PG 映射 5433 避免冲突），与全栈编排互不影响。

## 7. 关键技术决策

| 决策 | 选择 | 理由 | 备选 |
|------|------|------|------|
| Agent 引擎 | AgentScope Java 2.0（agentscope-harness） | Agentic 自主推理路线：内置 ReAct 循环、streamEvents 事件流、RuntimeContext 会话管理；2.0 Harness 层提供 Workspace/上下文压缩/Skill 运行时/子智能体/Middleware/Plan 模式（MVP 用 ReActAgent 内核，Phase 2 渐进采纳 Harness） | LangChain4j（需自研更多胶水代码）；Spring AI ChatModel（Workflow 路线，不适合自主推理） |
| RAG 管道 | Spring AI（Workflow 路线） | 固定步骤的分块/向量化/检索管道，不需要模型自主决策；TokenTextSplitter + OpenAiEmbeddingModel + PgVector | 自研分块/向量化（边界覆盖不足） |
| 向量存储 | PgVector（Spring AI） | 与业务库同库，运维简单；按维度分表 `vector_store_{dim}` | Milvus / Weaviate（多一套组件） |
| 认证 | Sa-Token JWT Simple | 轻量，Token 无状态，适合多租户上下文透传 | Spring Security（配置繁重） |
| 多租户 | MyBatis 拦截器自动注入 | 业务代码零感知，漏隔离风险低 | 手动 WHERE（易遗漏） |
| SSE 传输 | fetch + ReadableStream（非 EventSource） | 需要 POST body 与自定义 Header | EventSource（仅 GET） |
| 配置注入 | Spring Boot 环境变量覆盖 | Docker 化零代码改动，保留本地默认 | 多 profile yml（维护两份） |

## 8. 已知约束（MVP → Phase 2）

- **Skill 当前为提示词注入**：`buildSkillPrompt` 将绑定 Skill 描述写进系统提示词，ReAct 循环**尚未注册工具**（无 function calling）；SkillExecutor 执行基础设施已就绪，`SkillAgentTool`（实现 AgentScope `AgentTool` 接口）已有雏形。Phase 2 Skill 中心将把 Skill 注册为 AgentScope 工具实现真正的 function calling，并评估复用 `agentscope-harness` 的 HarnessSkillMiddleware / McpServerRegistrar 等内置能力。
- 列表接口无分页；知识库检索为纯向量（无混合检索/重排，详见 `improvements.md`）。
- ~~RBAC 仅有 workspace owner/member 两级~~ → 课题⑥已落地 4 角色（owner/admin/developer/observer）+ `WorkspaceRbacFilter` HTTP 层强制（详见 docs/api/rest-api.md「RBAC 角色」与 §13-15）。
