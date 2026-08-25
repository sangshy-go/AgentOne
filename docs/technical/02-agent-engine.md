# Agent 引擎技术方案

## 1. 概述

Agent 引擎负责：Agent 配置管理（六维配置 + 状态机）、系统提示词组装（AGENTS.md + 变量注入 + Skill + RAG + 历史）、基于 AgentScope ReActAgent 的同步/流式对话、模型解析链、会话与记忆管理。

> **依赖说明**：Maven 依赖为 `io.agentscope:agentscope-harness:2.0.0`，包含 ReActAgent 推理内核 + Harness 工程化层（Workspace / 上下文压缩 / Skill 运行时 / 子智能体 / Middleware / Plan 模式等）。MVP 阶段仅使用 ReActAgent 内核，Harness 能力的渐进采纳计划见 §11。

核心类（`agentone-agent` 模块）：

| 类 | 职责 |
|----|------|
| `ChatServiceImpl` | 对话编排核心（chat / chatStream / buildSystemPrompt / buildReActAgent） |
| `AgentServiceImpl` | Agent CRUD + 状态机 + 级联删除 |
| `VariableInjector` | 模板变量注入（`{{user_name}}` / `{{date}}` 等）；AGENTS.md 原文经变量注入后拼入系统提示词 |
| `MemoryService` | 历史上下文构建（滑动窗口/摘要策略） |
| `AgentScopeConfig` | AgentScope Model Bean 配置（全局默认模型） |
| `ModelConfig` / `MemoryConfig` | JSON 配置模型 |

## 2. Agent 配置与状态机

**六维配置**（存储于 `agent` 表）：

| 维度 | 字段 | 形态 |
|------|------|------|
| 基础信息 | name / description / category / icon | 文本 |
| 人格与指令 | agentsMd | Markdown 源码（AGENTS.md） |
| 模型策略 | modelConfig | JSON 字符串：`{chatModelId, temperature, topP, maxTokens, stream}` |
| 记忆 | memoryConfig | JSON 字符串：`{shortTermRounds, overflowStrategy}` |
| 能力绑定 | agent_skill_binding / agent_knowledge_binding | 关联表 |
| 高级 | advancedConfig | JSON 字符串 |

**状态机**（`AgentStatus` + `AgentAction`）：

```
draft ──▶ testing ──▶ published ──▶ stopped
  ▲          │            │            │
  └──────────┴── revert ──┴────────────┘
```

- 仅 `draft / testing / published` 可对话；`stopped` 拒绝（错误码 3003）。
- 已发布不可直接编辑，需先 `revert` 回草稿（3002）。
- 序列化为小写 code（`@JsonValue` on `AgentStatus.getCode()`），前端直接展示。
- 删除为物理删除，`@Transactional` 级联清理：消息（按 sessionId 批量 IN）→ 会话 → Skill 绑定 → 知识库绑定 → Agent。

## 3. 系统提示词组装

`buildSystemPrompt(agent, sessionId)` 按固定顺序拼接四段：

```
① AGENTS.md（经 VariableInjector 注入 {{变量}}）
   为空时回退 "You are a helpful assistant."
② buildSkillPrompt：绑定且启用的 Skill 列表（名称/ID/类型）
   —— MVP 为提示词注入，LLM「知道」有哪些工具；
      ReAct 循环尚未注册工具，function calling 属 Phase 2
③ buildRagContext：多知识库检索结果（见 03-rag-knowledge.md §5）
④ memoryService.buildContextPrompt(sessionId, maxRounds, overflowStrategy)
   默认最近 10 轮 + sliding_window 溢出策略
```

每段为空则跳过，段间以空行分隔。

## 4. 模型解析链

`buildReActAgent` 中的三级回退（Day 12 修复的核心链路）：

```
modelConfig.chatModelId 非空？
  ├─ 是 → model 表（ModelDO.modelId，如 qwen-plus）
  │       → model_provider 表（apiKey / baseUrl）
  │       （模型不存在 6002 / 供应商不存在 6011）
  └─ 否 → modelConfig 中直填的 model/apiKey/baseUrl（兼容字段）
          → 仍为空则 GenerateOptions.mergeOptions 回退全局
            agentscope.openai 配置（OPENAI_API_KEY / OPENAI_BASE_URL / AGENT_MODEL）
```

构建 `GenerateOptions`：modelName / temperature / topP / maxTokens / stream / apiKey / baseUrl，
并固定追加 `additionalBodyParam("enable_thinking", true)` —— AgentScope SDK 通过 `@JsonAnyGetter` 将其平铺进请求体顶层，Qwen3 等模型据此返回 `reasoning_content`。

`ReActAgent.builder()`：name / sysPrompt / model / generateOptions / maxIters=10 / defaultSessionId=sessionId。
**每请求新建实例**（ReActAgent 内部维护对话状态，不适合多线程共享；2.0 虽引入 Agent State + RuntimeContext 外置状态，MVP 仍采用每请求新建的简单方式）。

## 5. 同步对话 chat()

```
loadAgent → getOrCreateSession
→ buildUserContent(dto)  // 校验 message/attachmentIds 至少其一；图片→ImageBlock，文档→TextBlock(parsedText)
→ saveUserMessage(sessionId, content, attachmentsJson)  // 附件元信息 JSON 写入 chat_message.attachments
→ buildSystemPrompt → buildReActAgent
→ RuntimeContext(sessionId, userId, workspace_id, agent_id)
→ agent.call([userMsg], ctx).block() → Msg.getTextContent()  // userMsg.content = List<ContentBlock>
→ saveMessage(assistant) → 更新会话统计（messageCount/tokenCount，NPE 防御）
→ ChatResponseVO{sessionId, reply, tokenCount, durationMs, traceId}
```

## 6. 流式对话 chatStream()（SSE）

```
loadAgent → getOrCreateSession
→ buildUserContent(dto)  // 同 chat()：图片→ImageBlock，文档→TextBlock(parsedText)
→ saveUserMessage(sessionId, content, attachmentsJson)
Flux.concat(
  session 事件（data=sessionId，首推）,
  agent.streamEvents(userMsg, ctx).handle(...)
    ├─ TextBlockDeltaEvent    → event:delta    + 累加 fullReply
    ├─ ThinkingBlockDeltaEvent→ event:thinking
    ─ 其他事件（ModelCallStartEvent 等）静默忽略,
  done 事件（data=[DONE]）
).onErrorResume(err → event:error + data=err.message)
```

两个关键工程点：

1. **ThreadLocal 上下文捕获**：SSE 的 `doOnComplete` 在请求线程外触发，此时 `RuntimeContext` 已被 `JwtAuthFilter` 清除。`chatStream` 入口即捕获 userId/workspaceId，完成回调中临时 `RuntimeContext.set(ctx)` 再保存消息（MyBatis 租户字段自动填充依赖它），finally 中 clear。
2. **部分保存**：流中途失败（`doOnError`）时，已接收的部分回复连同 `[错误: ...]` 一并入库，避免内容丢失。

事件协议细节见 [docs/api/sse-protocol.md](../api/sse-protocol.md)。

## 7. 会话与记忆

- `chat_session`：按 agentId + userId 归属，记录 messageCount/tokenCount；标题默认取首条消息。
- `chat_message`：role = user/assistant，存 tokenCount（`TokenCounter` 估算）；`attachments` JSONB 字段存附件元信息列表（仅 user 消息）。
- 上下文压缩：`MemoryService.buildContextPrompt` 按 `shortTermRounds` 取最近 N 轮，超出按 `overflowStrategy`（默认 sliding_window）处理，输出为文本段落拼入系统提示词。
- 删除会话时级联删除所属附件记录（`chatAttachmentMapper.deleteBatchIds`）。

## 8. 对话附件

### 8.1 数据模型

**`chat_attachment` 表**（V22 迁移）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID 主键 |
| workspace_id | varchar(36) | 工作空间归属（隔离） |
| user_id | varchar(100) | 上传者（隔离） |
| file_name | varchar(255) | 原始文件名 |
| mime_type | varchar(100) | MIME 类型 |
| file_size | bigint | 字节数 |
| kind | varchar(10) | `image` / `document` |
| data | bytea | 文件字节（PG 内存储，无磁盘） |
| parsed_text | text | 文档解析结果（≤200k 字符） |
| created_at | timestamp | 上传时间 |

**`chat_message.attachments` JSONB**：

```json
[
  {"id":"att-1","kind":"image","fileName":"photo.png","fileSize":102400,"mimeType":"image/png"},
  {"id":"att-2","kind":"document","fileName":"notes.txt","fileSize":512,"mimeType":"text/plain"}
]
```

仅 user 消息写入；assistant 消息该字段为 null。

### 8.2 上传链路

```
POST /api/chat/attachments（multipart `file`）
  ↓
扩展名白名单 → 图片 png/jpg/jpeg/webp/gif ≤10MB；文档 pdf/docx/txt/md/csv ≤20MB
  ↓
Tika MIME 嗅探（防扩展名伪装）→ 拒绝 text/html、application/xhtml+xml、image/svg+xml
  ↓
文档即时 DocumentParser.parse → 失败拒绝入库（4014），成功截断至 200k 字符存 parsed_text
  ↓
ChatAttachmentDO 入库（workspace_id + user_id 绑定）→ 返回 AttachmentVO（元信息）
```

**核心类**：`ChatAttachmentServiceImpl`（agentone-agent 模块）。

### 8.3 多模态集成（ChatServiceImpl）

`buildUserContent(dto)` 校验 `message` 与 `attachmentIds` 至少其一，并按类型构建 `ContentBlock` 列表：

| 附件类型 | AgentScope Block | 注入方式 |
|---------|------------------|---------|
| 图片 | `ImageBlock(Base64Source(mediaType, base64Data))` | 多模态通道，需视觉模型（如 qwen-vl） |
| 文档 | `TextBlock("[附件: fileName]\n" + parsedText)` | 按 `agentone.chat.attachment-max-tokens`（默认 6000）截断后注入 prompt |

落库 `chat_message.content` 保持纯文本（不污染 RAG query 与历史上下文）；附件元信息 JSON 写入 `attachments` 字段。

**调用链路变化**：

```
chat() / chatStream()
  ↓
UserContent = buildUserContent(dto)  // 校验 + 构建 blocks + 附件元信息 JSON
  ↓
saveUserMessage(sessionId, content, attachmentsJson)  // 独立命名避免与 skillCalls 重载冲突
  ↓
agent.call(userMsg, ctx)  // userMsg.content = blocks（多模态）
  ↓
saveMessage(assistant)  // 正常 assistant 消息，attachments=null
```

### 8.4 前端渲染

**ChatDrawer.vue**：

1. **输入区**：回形针按钮触发隐藏 `<input type=file multiple accept="...">`，选中即上传至 `/api/chat/attachments`。
2. **等待发送**：`pendingAttachments` chip 行（图片显示 24px 缩略图，文档显示文件名+大小），可随时移除。
3. **发送时**：`chatStream` 带 `attachmentIds` 参数；乐观 user 消息带 `attachments` 元信息数组。
4. **历史渲染**：
   - 图片：`<img src="/api/chat/attachments/{id}">`（同源 Cookie 自动携带）
   - 文档：chip 显示文件名+大小，点击新标签页打开/下载
5. **静态提示**：输入区有图片附件时显示「图片识别需 Agent 绑定视觉模型（如 qwen-vl 系列）」。

**服务层**：`uploadAttachment(file)`（FormData + multipart + timeout 120s）、`attachmentUrl(id)`、`parseAttachments(raw)`（兼容 JSON 字符串/已解析数组）。

### 8.5 错误码

| 码 | 含义 |
|----|------|
| 4010 | 附件不存在 |
| 4011 | 无权访问该附件（跨工作空间/用户） |
| 4012 | 文件类型不允许（扩展名或 MIME 嗅探拒绝） |
| 4013 | 文件超过大小限制 |
| 4014 | 文档解析失败 |
| 4015 | API Key 调用不支持附件，请使用 /api 路径 |

### 8.6 已知限制与后续演进

1. **BYTEA 存储**：当前文件存 PG `bytea`，上限 10/20MB 可接受；后续可迁 OSS（MinIO/S3）并保留 `data` 字段为空。
2. **视觉模型缺失**：现网仅 qwen3.7-plus（文本），图片附件会触发模型报错，经 SSE error 通道透出；静态提示已告知用户。不为此加 model 表 modality 字段（过度设计）。
3. **文档解析复用**：直接复用知识库 `DocumentParser`（Tika），不重复造轮子。

## 9. Skill 框架（agentone-skill）

```
SkillExecutor（接口）
  ├─ execute(SkillInvocation, Context) → SkillResult
  ├─ getDescriptor() → SkillDescriptor（名称/描述/参数 schema）
  └─ isAvailable()
SkillRegistry：执行器注册表（SkillAutoRegisterConfig 启动时自动注册内置 Skill）
内置执行器：HttpRequestSkill（WebClient）、CodeExecuteSkill
知识库检索：未走 Skill 通道，直接由 buildRagContext 注入（检索即上下文）
绑定：agent_skill_binding（agentId + skillId + enabled），SkillController 管理
```

**现状与边界**：绑定信息当前仅经 `buildSkillPrompt` 以文本形式告知 LLM，`SkillExecutor.execute` 的执行通路已就绪但尚未接入 ReAct 工具调用。`SkillAgentTool.java` 已实现 AgentScope 的 `AgentTool` 接口（import `io.agentscope.core.tool.AgentTool`），是工具化接入的雏形。Phase 2 的 Skill 中心将完善此路径，实现真正的 function calling，并评估复用 `agentscope-harness` 的 `HarnessSkillMiddleware`（Skill 注册/执行/沙箱隔离）和 `McpServerRegistrar`（MCP 工具发现）。详见 §11.3 演进路线。

## 10. 模型供应商（两层结构）

```
model_provider（供应商：name / provider=openai / apiKey / baseUrl）
  └── model（模型：modelType=chat|embedding|rerank|image2text / modelId=API 模型名 /
             contextSize / maxTokens / dimensions 自动探测）
```

- 连通性检测：`POST /api/model-providers/{id}/check`（真实轻量调用）与 `/api/model-check/check`（不落库快速检测）。
- Chat 解析链见 §4；Embedding 解析链见 03-rag-knowledge.md §4。

## 11. 代码阅读建议

1. 从 `ChatServiceImpl.chatStream()` 读起（对话全链路在此编排）；
2. 再看 `buildSystemPrompt()` 的四段组装与 `buildReActAgent()` 的模型解析链；
3. 对话附件全链路：`ChatAttachmentController` → `ChatAttachmentServiceImpl.upload` → `ChatServiceImpl.buildUserContent` → `ChatDrawer.vue`；
4. 然后 `AgentServiceImpl` 的状态机与级联删除；
5. 最后 `skill/core/` 四件套（Executor/Registry/Descriptor/Invocation）理解 Skill 框架。

## 12. AgentScope 2.0 Harness 能力与演进路径

### 12.1 架构分层

AgentScope Java 2.0 分为两层（项目依赖 `agentscope-harness` 已包含两层）：

```
┌─────────────────────────────────────────────────────┐
│  Harness 工程化层（io.agentscope.harness）            │
│  入口类：HarnessAgent                                │
│                                                     │
│  ┌─ Workspace（Agent 资产 + 运行时数据统一管理）       │
│  ├─ 上下文压缩（CompactionMiddleware，4 道防线）      │
│  ├─ 长期记忆（MemoryFlush → 每日流水 → 蒸馏 MEMORY.md）│
│  ├─ Skill 运行时（注册/执行/沙箱隔离/用户级隔离）      │
│  ├─ 子智能体（Fork/Spawn，同步/异步/远程）            │
│  ├─ Plan 模式（PlanEnter/PlanExit，规划-执行切换）     │
│  ├─ Middleware（17 个内置中间件）                     │
│  ├─ MCP 集成（McpServerRegistrar / ToolsConfig）     │
│  └─ Channel（IM 平台对接）                          │
├─────────────────────────────────────────────────────┤
│  ReActAgent 推理内核（io.agentscope.core）  ← MVP 使用 │
│                                                     │
│  ReAct 循环 / Model / Tool / Event / Msg            │
│  RuntimeContext / GenerateOptions / Middleware 基础   │
└─────────────────────────────────────────────────────┘
```

**关键设计**：从 ReActAgent 切到 HarnessAgent，底层推理内核不变，业务代码不需重写——Harness 是"叠加"而非"替换"。

### 12.2 MVP 阶段的使用边界

| 能力 | MVP 状态 | 自研替代 | 说明 |
|------|---------|---------|------|
| ReAct 推理循环 | ✅ 使用 | — | `ReActAgent.builder()` + `streamEvents()` |
| RuntimeContext | ✅ 使用 | — | 传入 sessionId/userId/workspace_id |
| 事件流 | ✅ 使用 | — | TextBlockDeltaEvent / ThinkingBlockDeltaEvent |
| Workspace | ❌ 未用 | DB 层 workspace 表 + MyBatis 租户拦截器 | 自研方案与 MyBatis-Plus 深度集成，切换成本高 |
| 上下文压缩 | ❌ 未用 | MemoryService 滑动窗口（最近 N 轮） | 自研方案简单可控，但缺少工具结果截断、关键状态保留 |
| 长期记忆 | ❌ 未用 | 无 | Phase 2+ 评估 |
| Skill 运行时 | ❌ 未用 | buildSkillPrompt 文本注入（无真正 function calling） | **Phase 2 必须解决** |
| 子智能体 | ❌ 未用 | 无 | Phase 2+ 评估 |
| Middleware | ❌ 未用 | 无 | 文章实践表明 Context-Reminder 防"Lost in the Middle"很有价值 |
| MCP 集成 | ❌ 未用 | 无 | **Phase 2 应复用 McpServerRegistrar** |

### 12.3 Phase 2 演进路线

按优先级排列，渐进采纳 Harness 能力（不做全面迁移）：

| 优先级 | 内容 | 方式 | 影响范围 |
|--------|------|------|---------|
| **P0** | Skill 注册为 AgentScope 工具（function calling） | 完善 `SkillAgentTool`（已有 `AgentTool` 接口雏形），结合 `HarnessSkillMiddleware` | ChatServiceImpl + SkillRegistry |
| **P1** | MCP 集成 | 复用 `McpServerRegistrar` + `ToolsConfig`，不需自研 MCP 协议连接 | 新增 MCP 模块 |
| **P1** | Middleware 机制 | 至少引入 Context-Reminder（每轮强制注入关键上下文），防"Lost in the Middle" | ChatServiceImpl.buildSystemPrompt |
| **P2** | 上下文压缩 | 评估 `CompactionMiddleware` 替代手写滑动窗口 | MemoryService |
| **P3** | 整体评估 HarnessAgent 入口 | P0-P2 落地后，评估是否将 `ReActAgent.builder()` 替换为 `HarnessAgent.builder()` | 全局 |

### 12.4 不迁移的理由（MVP 阶段）

1. **多租户体系冲突**：AgentOne 用 MyBatis-Plus 拦截器自动注入 `workspace_id`，HarnessAgent 用 Workspace + Abstract FileSystem（DB/OSS/Sandbox）。两套体系切换是大手术，MVP 不值得。
2. **当前对话链路已验证**：buildReActAgent → streamEvents → SSE 映射，代码不到 100 行，稳定运行。
3. **排期约束**：Phase 2 有 10 天工作量（Skill 中心/MCP/IM Bot/监控/RBAC），全面迁移会挤爆排期。
4. **渐进路线可行**：Harness 的设计就是"能力可插"——不需要全用，按需启用即可。
