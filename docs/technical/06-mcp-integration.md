# MCP 集成技术方案

> Phase 2 · 课题④（MCP 集成）
> 状态：已交付（后端全链路 + 28 单测 + 本地 stdio mock 运行时验证 + 前端 MCP 管理页）
> 前置阅读：[04-skill-system.md](04-skill-system.md)（SkillExecutor / SkillRegistry / 虚拟挂载机制）

## 1. 概述

MCP（Model Context Protocol）集成让 AgentOne 可以接入任意实现了 MCP 协议的外部工具源（本地脚本、远程服务），把发现的工具以**虚拟 Skill** 形式纳入统一的 Skill 体系。

核心能力：

| 能力 | 说明 |
|------|------|
| Server 管理 | 每工作空间登记若干 MCP Server：stdio / sse / streamable_http 三种传输，CRUD + 启用状态 |
| 连接与服务发现 | `connect` 完成协议握手 + `tools/list`；工具逐个注册为虚拟 Skill |
| 虚拟工具注册 | 工具不落 `skill` 表，ID 形如 `mcp-{serverId}-{toolName}`，生命周期与连接一致 |
| 全链路复用 | 注册后自动获得：Agent 绑定、function calling、调用审计、Skill 调试器 |
| 启动自愈 | `McpServerBootstrap` 应用启动时重连所有 active Server 并恢复工具注册 |
| 租户隔离 | `mcp_server` 表受租户拦截器保护；Registry 内工具按描述符 workspaceId 过滤 |

与其他模块的关系：本模块是 Skill 体系的"第四个执行器来源"，不新增任何对话/绑定/审计逻辑——所有集成点都在 04-skill-system.md 描述的既有抽象上。

## 2. 架构设计

### 2.1 组件与职责

```
McpServerController（REST，/api/mcp-servers）
   ▼
McpServerServiceImpl（管理主链路：CRUD / connect / disconnect / listTools）
   ├─→ McpServerMapper          mcp_server 表（租户拦截器自动隔离）
   ├─→ McpConnectionManager     连接资源唯一持有者（stdio=子进程，sse/http=长连接）
   │      └─ AgentScope McpClientBuilder / McpClientWrapper（MCP SDK 0.17.0）
   └─→ SkillRegistry            虚拟工具注册表（与 builtin/api 共用）
          ▲
McpSkillExecutor（每工具一实例，无状态，持 client 引用）

McpServerBootstrap（ApplicationRunner）── 启动重连 active Server
```

**连接与执行分离**是关键结构决策：连接是重资源且生命周期独立于单次调用，由 `McpConnectionManager` 独占持有（含 `@PreDestroy` 兜底）；执行器无状态，断连/删除时注销执行器即可，无需关心资源释放。

### 2.2 连接 → 发现 → 注册流程

```
connect(serverId)
  ├─ requireServer（5009，selectById 自带租户过滤）
  ├─ McpConnectionManager.open(server)
  │    ├─ close(serverId)                      同 Server 重复 open 先关旧连接
  │    ├─ 按 transport 构建客户端
  │    │    stdio → stdioTransport(command, args, Map.of())   env 必须非 null
  │    │    sse / streamable_http → 设 url + 注入 headers
  │    └─ buildSync + initialize().block(timeout)   握手失败先 safeClose 再抛
  ├─ client.listTools().block(timeout)
  ├─ unregisterServerTools(serverId)           先注销旧工具（重连不残留）
  ├─ 逐工具 skillRegistry.register(new McpSkillExecutor(...))
  └─ 更新 last_connected_at，返回工具列表
```

### 2.3 一次 MCP 工具调用（对话中）

与所有 Skill 完全一致（见 04 §2.2）：LLM function call → SkillAgentTool → `McpSkillExecutor.execute` → `client.callTool(name, params).block(timeout)` → `isError`/null 判失败，文本内容 + structuredContent 组装 `SkillResult` → 审计落 `skill_call_log`。断连后执行器已被注销，buildToolkit 装配时 `getExecutor` 返回空即自动跳过（降级为工具不可用，不阻断对话）。

## 3. 核心实现

### 3.1 McpSkillExecutor（工具 → SkillExecutor 适配）

- 虚拟 ID：`skillIdOf(serverId, toolName)` = `"mcp-" + serverId + "-" + toolName`；`serverIdOf` 反向解析（serverId 为 32 位无连字符 hex，`split("-", 3)` 无歧义）。
- 描述符：`type=mcp`、`source=Server 名`、`workspaceId=归属空间`、`name="Server 名 / toolName"`；`McpSchema.JsonSchema` 转标准 JSON Schema Map（空 schema 降级为 `{"type":"object","properties":{}}`）。
- 结果适配：`CallToolResult.content` 的文本块拼接为 text，非文本块以 `[XxxContent 非文本内容]` 占位（不静默丢失）；`isError=TRUE` → `SkillResult.failure`。
- `isAvailable()` = `client.isInitialized()`，断连即不可用。

### 3.2 McpConnectionManager（连接生命周期）

- `open`：幂等（先关旧）；握手超时受 `timeoutMs` 约束；**握手失败先释放资源再抛**，避免子进程/连接泄漏。
- `close`：幂等；`@PreDestroy closeAll` 应用退出兜底。
- 已知坑：SDK `stdioTransport` 内部 `new HashMap<>(env)`，env 传 null 直接 NPE —— 固定传 `Map.of()`。

### 3.3 McpServerServiceImpl（管理策略）

| 操作 | 行为 | 错误码 |
|------|------|--------|
| create | 校验 transport 枚举 + stdio 需 command / http 类需 url；默认 status=active、timeoutMs=30000 | 5010 |
| update | transport/url/command 变化且已连接 → 注销工具 + 关旧连接（需重新 connect） | 5009 / 5010 |
| delete | 绑定保护：`agent_skill_binding.skill_id` 按 `mcp-{serverId}` 前缀计数 > 0 拒绝 | 5012 |
| connect / disconnect | 见 §2.2；disconnect 保留配置与 Server 行 | 5009 / 5011 |
| listTools | Registry 按 `mcp-{serverId}-` 前缀过滤（工具在内存，不落库） | 5009 |

租户隔离零特判：`mcp_server` 不在拦截器 IGNORE_TABLES，selectById/selectPage 自动过滤；跨空间访问表现为 5009（"不存在"）。

### 3.4 McpServerBootstrap（启动自愈）

与课题② ApiSkillBootstrap 同模式：`selectAllActiveMcpServers()`（@InterceptorIgnore，启动期无请求上下文）→ 逐 Server 手动 `RuntimeContext.set(... workspaceId)` → `connect`（单个失败 try/catch 不阻断）→ finally clear。

### 3.5 离线验证工具

`scripts/mock-mcp-stdio-server.py`：零第三方依赖的最小 stdio MCP Server（换行分隔 JSON-RPC 2.0），支持 initialize / notifications/initialized / ping / tools/list / tools/call，提供 echo(text) 与 add(a,b) 两个工具。登记方式：transport=stdio, command=python3, args=["<绝对路径>/scripts/mock-mcp-stdio-server.py"]。

## 4. 数据库设计

### 4.1 mcp_server（V16 建表）

```sql
id / workspace_id(→workspace) / name / description
transport(stdio|sse|streamable_http) / url / command
args JSONB DEFAULT '[]' / headers JSONB DEFAULT '{}'
timeout_ms DEFAULT 30000 / status(active|disabled)
last_connected_at / created_at / updated_at
```

索引：`workspace_id`、`status`。受租户拦截器保护。

### 4.2 V17：skill_id 列扩宽

`agent_skill_binding.skill_id` 与 `skill_call_log.skill_id` 由 VARCHAR(36) 扩至 **VARCHAR(200)**：MCP 虚拟 ID = `mcp-` + 32 位 hex serverId + `-` + 外部 toolName（可达数十字符），超原 UUID 长度，绑定与审计落库会报 value too long。`skill` 表自身不受影响（只存 api/market Skill，ID 仍为 UUID）；`skill_id` 外键已在 V15 移除，扩列无约束冲突。

## 5. API 设计

统一前缀 `/api/mcp-servers`，Cookie 认证（S7）。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/mcp-servers` | 创建（body: McpServerDTO） |
| PUT | `/api/mcp-servers/{id}` | 更新（连接配置变化会关闭旧连接） |
| DELETE | `/api/mcp-servers/{id}` | 删除（工具仍被绑定拒绝 5012） |
| GET | `/api/mcp-servers?page&size` | 分页列表（含 connected / toolCount） |
| GET | `/api/mcp-servers/{id}` | 详情 |
| POST | `/api/mcp-servers/{id}/connect` | 连接并发现工具，返回 `List<McpToolVO>` |
| POST | `/api/mcp-servers/{id}/disconnect` | 断开（保留配置） |
| GET | `/api/mcp-servers/{id}/tools` | 已发现的工具列表 |

错误码（Skill 段 5xxx 顺延；6xxx 段归知识库模块，勿复用）：

| code | 含义 |
|------|------|
| 5009 | MCP Server 不存在（含跨空间访问） |
| 5010 | 配置非法（transport 枚举 / 缺 command / 缺 url） |
| 5011 | 连接失败 / 工具发现失败（携带根因消息） |
| 5012 | 该 Server 的工具仍被 Agent 绑定，拒绝删除 |

跨租户绑定/测试/调试 MCP 工具复用 Skill 段 5004。

## 6. 关键技术决策

| # | 决策 | 选择 | 理由 | 备选方案 |
|---|------|------|------|---------|
| M1 | MCP 工具存哪 | 虚拟 Skill（只进 Registry，不落 skill 表） | 工具随连接生灭，落库会残留"已断开的死工具行"；与 builtin 虚拟挂载同机制，绑定/审计/调试零改造 | 落 skill 表 + 状态字段（否决：同步负担） |
| M2 | 连接资源归属 | McpConnectionManager 独占 + @PreDestroy | stdio=子进程，必须有唯一收口点防泄漏 | 执行器各持连接（否决：无法统一释放） |
| M3 | url/command 是否过 SSRF 拦截 | **不过** | stdio 本身即"执行本地命令"的能力，url/command 由工作空间管理员配置（与 Dify/n8n 同类产品一致）；内网 MCP Server 是合法目标。信任边界 = 空间管理员权限，与 API Skill 的"LLM 可控目标"是两套信任模型 | 一律 SSRF 拦截（否决：stdio 无法工作，且误伤内网场景） |
| M4 | 启动恢复 | Bootstrap 重连 active Server | 与 ApiSkillBootstrap 同模式；单个失败不阻断 | 懒加载（首次使用时连）（否决：首条消息延迟不可控） |
| M5 | 握手/发现超时 | 复用 Server 的 timeoutMs + Reactor block | 坏 Server 不能拖死请求线程 | 无限等待（否决） |

## 7. 性能与扩展

- 连接数 = active 且已连接的 Server 数，通常个位数；每连接资源由管理器收口，可监控可扩展。
- `listTools`/`toolCount` 为内存前缀扫描，Registry 规模（几十～几百描述符）下开销可忽略。
- 扩展：sse/streamable_http 传输的运行时验证依赖外网环境（本机离线仅验证 stdio 全链路）；MCP 协议的 prompts/resources 能力未接入（仅 tools），后续按需扩展。

## 8. 测试要点

单测（28 例，全绿）：

- `McpSkillExecutorTest`（12）：skillIdOf/serverIdOf 互转、描述符构建、execute 成功/isError/null/异常四路径、isAvailable（mock `McpClientWrapper`，真实 `McpSchema.Tool`）。
- `McpServerServiceImplTest`（16）：create 默认值、validate 5010 各分支、connect 失败 5011、delete 保护 5012、ownsServer、listTools 前缀过滤、update 脏连接关闭。

运行时验证（本地 stdio mock + 真实 API + Cookie）：create → connect 发现 2 工具 → 技能列表合并 → 绑定真实 Agent → 删除保护 5012 → 解绑 → 删除 → tools 5009；重启自动重连（Bootstrap）；跨工作空间不可见/不可绑定（5009/5004/列表过滤）；调试器调试 MCP 工具写审计（V17 后长 ID 落库正常）。

**已知环境限制**：sse / streamable_http 传输在无外网环境无法运行时验证，依赖单测 + SDK 成熟度保障。
