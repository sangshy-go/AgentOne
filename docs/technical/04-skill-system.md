# Skill 系统技术方案

> Phase 2 · 课题①（Skill 工具化）+ 课题②（Skill 中心后端）
> 状态：已交付（后端全链路 + 单元测试 + 运行时验证）；前端 Skill 中心页面与真实 LLM function calling 联调待后续。

## 1. 概述

Skill 系统负责 Agent 的"动手能力"：把知识库检索、HTTP 调用、代码执行、用户自建的 API 封装成统一的**可执行技能**，并在对话中由 LLM 通过 **function calling** 自主决定何时调用。

核心能力：

| 能力 | 说明 |
|------|------|
| 统一执行器抽象 | `SkillExecutor` 接口统一 builtin / api（后续 mcp）三类技能 |
| 工具化（Phase 2 核心改造） | Skill 注册为 AgentScope `Toolkit` 原生工具，**替换 MVP 的提示词注入**，LLM 以标准 function call 调用 |
| 虚拟挂载 builtin | 内置 Skill 不落库，只存在于内存 `SkillRegistry`；列表/绑定时动态合并 |
| Skill 中心（API 模式） | 用户通过 REST API 创建 HTTP 封装 Skill：CRUD + 测试调用 + 启动/运行时动态注册 |
| 调用链审计 | 每次 Skill 执行写 `skill_call_log`（best-effort，不影响主对话链路） |
| SSRF 防护 | 所有受用户/LLM 控制目标地址的出站请求统一过 `UrlSafetyUtil` |

与其他模块的关系：

```
ChatServiceImpl（agent 模块）
   │ buildToolkit：按 Agent 绑定装配工具
   ▼
SkillAgentTool（agent 模块，AgentTool 适配器）
   │ callAsync：恢复 RuntimeContext → execute → 写审计
   ▼
SkillExecutor（skill 模块接口）
   ├─ builtin：HttpRequestSkill / CodeExecuteSkill（skill 模块）
   ├─ builtin：KnowledgeSearchSkill（agent 模块，依赖 KnowledgeService）
   └─ api：ApiSkillExecutor（skill 模块，DB 配置驱动，每 Skill 一实例）
```

## 2. 架构设计

### 2.1 装配流程（启动期 + 请求期）

```
启动期:
  SkillAutoRegisterConfig（ApplicationRunner）
      → 收集容器内全部 SkillExecutor Bean（3 个 builtin）→ SkillRegistry.register
  ApiSkillBootstrap（ApplicationRunner）
      → SkillMapper.selectAllActiveApiSkills()（@InterceptorIgnore 跳过租户过滤）
      → 每行构建 ApiSkillExecutor → SkillRegistry.register

请求期（每次对话）:
  ChatServiceImpl.buildReActAgent
      → buildToolkit(agentId, toolCallSink)
          ├─ agentSkillService.listBindings(agentId)     // 该 Agent 的启用绑定
          ├─ skillRegistry.getExecutor(skillId)          // builtin / api 统一查 Registry
          ├─ toolkit.registerAgentTool(new SkillAgentTool(executor, recorder))
          └─ toolkit.setChunkCallback(...)               // 工具调用旁路 → chat_message.skill_calls
      → ReActAgent.builder().toolkit(toolkit)
```

### 2.2 一次 function call 的完整数据流

```
LLM 决定调用工具（function call）
  ▼
AgentScope Toolkit 分发 → SkillAgentTool.callAsync(param)
  ▼  [Mono.fromCallable，subscribeOn boundedElastic]
1. buildContext(param)：从 AgentScope RuntimeContext 还原 userId/workspaceId/agentId
2. RuntimeContext.set(context)          ← ThreadLocal 恢复（租户拦截器依赖它）
3. executor.execute(invocation, context) ← 真正执行（HTTP/检索/代码）
4. finally 恢复原 ThreadLocal
5. recordCallLog(...) → SkillCallLogRecorder → skill_call_log（try/catch 兜底）
  ▼
ToolResultBlock 返回 LLM → 继续 ReAct 循环
```

**为什么第 2 步是必须的**：MyBatis-Plus 租户拦截器从 `RuntimeContext`（ThreadLocal）取 workspace_id，取不到直接抛异常。Reactor 调度把执行切到 boundedElastic 线程池，ThreadLocal 不会自动传播，必须在工具线程内手动 set/restore（`SkillAgentTool.callAsync` 的 try/finally）。

### 2.3 虚拟挂载（builtin 不落库）

builtin 的技能定义在代码里（`SkillDescriptor`），落库只会带来"每个工作空间复制一份"的同步负担，且 `skill` 表主键是单列 `id`，按工作空间复制会主键冲突。因此：

- `skill` 表只存 api / market 等**用户创建**的 Skill；
- builtin 只存在于 `SkillRegistry`（内存）；
- 列表（`listSkills` 第一页置顶合并）与绑定（`bind` 时 Registry 兜底解析）两处做"虚拟挂载"；
- `agent_skill_binding` 仍落库（租户拦截器隔离），其 `skill_id` 可以是 builtin 虚拟 ID。

**代价**：`agent_skill_binding.skill_id → skill.id` 的外键必须移除（V15 迁移），引用完整性改由服务层保障（见 §6 决策 D4）。

## 3. 核心实现

### 3.1 SkillExecutor / SkillDescriptor（统一抽象）

```java
public interface SkillExecutor {
    SkillResult execute(SkillInvocation invocation, Context context);
    SkillDescriptor getDescriptor();   // id/name/description/type/inputSchema/...
    default boolean isAvailable() { return true; }
}
```

`inputSchema` 是 **JSON Schema**，直接作为 function calling 的 parameters 定义发给 LLM（`SkillAgentTool.getParameters()` 原样返回）。

### 3.2 SkillAgentTool（SkillExecutor → AgentScope AgentTool 适配器）

关键职责（`agentone-agent/tool/SkillAgentTool.java`）：

1. **工具名清洗**：skill ID → 合法 function name，`builtin-http-request` → `builtin_http_request`（`sanitizeToolName`，只保留 `[a-zA-Z0-9_]`）。
2. **上下文恢复**：从 `param.getRuntimeContext()` 取 `userId`、`workspace_id`、`agent_id` 构建业务 `Context`，并在工具线程 `RuntimeContext.set/restore`。
3. **审计**：执行后调 `SkillCallLogRecorder.record(...)` 落 `skill_call_log`。
4. **结果适配**：`SkillResult.success` → `ToolResultBlock.text(JSON)`；失败 → `ToolResultBlock.error`。

### 3.3 ChatServiceImpl.buildToolkit（按 Agent 装配）

```java
private Toolkit buildToolkit(String agentId, List<Map<String, Object>> toolCallSink) {
    Toolkit toolkit = new Toolkit();
    try {
        for (AgentSkillBindingVO binding : agentSkillService.listBindings(agentId)) {
            if (!Boolean.TRUE.equals(binding.getEnabled())) continue;   // 禁用的绑定不装配
            skillRegistry.getExecutor(binding.getSkillId()).ifPresent(executor ->
                    toolkit.registerAgentTool(new SkillAgentTool(executor, skillCallLogRecorder)));
        }
    } catch (Exception e) { log.warn(...); }   // 装配失败降级为"无工具"，不阻断对话
    toolkit.setChunkCallback((use, result) -> {
        toolCallSink.add(Map.of("name", use.getName(), "input", use.getInput()));
    });
    return toolkit;
}
```

- `toolCallSink` 是 `CopyOnWriteArrayList`，chunk 回调把每次工具调用旁路记录，最终随助手消息序列化进 `chat_message.skill_calls`（对话可留痕的组成部分）。
- MVP 的 `buildSkillPrompt()`（把技能说明拼进 system prompt）**已整体删除**——工具定义由 Toolkit 原生下发给 LLM。

### 3.4 ApiSkillExecutor（API 模式执行器）

非 Spring Bean，**DB 每行一个实例**，构造入参 `(SkillDO, WebClient, ObjectMapper)`。

- 目标地址 / 方法 / 头 / 超时来自 `skill.config`（JSONB）：`{url, method, headers, timeout}`；
- LLM 提供的 `params` 作为载荷：**POST/PUT → JSON 请求体；GET/DELETE → query 参数**；
- 执行期**二次** `UrlSafetyUtil.validate(url)`（创建时已校验过一次，防 DNS 变化/配置漂移）；
- `getDescriptor()` 从 `SkillDO` 构建，`inputSchema` 解析失败降级为空 object schema（不让坏数据炸掉注册）。

### 3.5 Skill 中心服务（SkillServiceImpl）

| 操作 | 行为 | 错误码 |
|------|------|--------|
| create | 校验 config/schema → 落库（type=api, source=custom, status=active）→ `registry.register` | 5007 配置非法 / 5008 地址不安全 |
| update | `requireApiSkill` → 校验 → updateById → `registry.register`（同 key 覆盖） | 5002 / 5006 / 5007 / 5008 |
| delete | `requireApiSkill` → 有绑定拒绝 → deleteById → `registry.unregister` | 5005 仍被绑定 |
| test | Registry 优先；不在则按 DB 行临时构建执行器；直接 `execute` 返回真实结果 | 5002 / 5006 |

- `requireApiSkill`：`selectById`（租户拦截器天然过滤跨工作空间）+ `type=="api"` 校验，builtin 不可改。
- 写操作同步维护 Registry，**创建成功后 Agent 立即可绑定、对话可用**，无需重启。

### 3.6 UrlSafetyUtil（SSRF 防护）

只放行 http/https；解析主机后拒绝环回 / 私网（site-local）/ 链路本地 / 通配地址——即拦截 `127.0.0.1`、`169.254.169.254`（云元数据）、`10.*` / `192.168.*` 等。`HttpRequestSkill` 与 `ApiSkillExecutor` 共用。已知局限：存在 DNS 重绑定理论风险（检查与连接两次解析可能不一致），生产建议对解析出的地址直连（列为后续增强，见 improvements.md）。

## 4. 数据库设计

### 4.1 skill（仅存 api / market，V2 建表）

`id(PK) / workspace_id / name / type / source / description / input_schema(JSONB) / output_schema(JSONB) / config(JSONB) / version / status / installed_at`。受租户拦截器保护。

### 4.2 agent_skill_binding（V2 建表，V14 补 workspace_id）

`id / agent_id / skill_id / skill_version / config_override(JSONB) / enabled / created_at`。
**V15：移除 `skill_id → skill.id` 外键**（builtin 虚拟 ID 在 skill 表无对应行），`agent_id` 外键保留。

### 4.3 skill_call_log（V4 建表，课题①启用写入）

`id / workspace_id / agent_id / skill_id / session_id / trace_id / input_params(JSONB) / output_result(JSONB) / duration_ms / token_count / status / error_message / created_at`。
受租户拦截器保护（不在 IGNORE_TABLES）；写入由 `SkillCallLogRecorder` best-effort 完成（异常仅告警，不阻断对话）。

## 5. API 设计

统一前缀 `/api/skills`，Cookie 认证（S7：token 仅走 HttpOnly Cookie，响应体不下发）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skills?page&size` | 列表：第一页置顶合并 builtin 虚拟项 |
| POST | `/api/skills` | 创建 API Skill（body: SkillDTO） |
| PUT | `/api/skills/{id}` | 更新 API Skill |
| DELETE | `/api/skills/{id}` | 删除 API Skill（有绑定拒绝 5005） |
| POST | `/api/skills/{id}/test` | 测试调用（body: `{params}`，返回真实 SkillResult） |
| GET | `/api/skills/bindings/{agentId}` | Agent 的绑定列表 |
| POST | `/api/skills/bind` | 绑定（body: BindSkillDTO） |
| DELETE | `/api/skills/bindings/{bindingId}` | 解绑 |
| PUT | `/api/skills/bindings/{bindingId}/toggle?enabled=` | 启用/禁用绑定 |

新增错误码：

| code | 含义 |
|------|------|
| 5001 | 已绑定此 Skill |
| 5002 | Skill 不存在 |
| 5003 | 绑定记录不存在 |
| 5004 | 无权绑定其他工作空间的 Skill |
| 5005 | Skill 仍被 Agent 绑定，拒绝删除 |
| 5006 | 内置 Skill 不可修改 / 不支持直接测试 |
| 5007 | Skill 配置 / Schema 非法 |
| 5008 | Skill 地址不安全（SSRF 拦截） |

## 6. 关键技术决策

| # | 决策 | 选择 | 理由 | 备选方案 |
|---|------|------|------|---------|
| D1 | Skill 如何被 LLM 调用 | AgentScope Toolkit 原生 function calling | 标准工具协议，LLM 自主决策调用时机与参数；提示词注入无法结构化传参、不可审计 | 提示词注入（MVP 做法，已废弃） |
| D2 | builtin 存哪 | 虚拟挂载（只进 Registry，不落库） | 避免按工作空间复制同步 + 单列主键冲突 | 每工作空间落一行（否决） |
| D3 | 启动期如何加载 api Skill | `@InterceptorIgnore` 专用 Mapper 方法 | MP 原生机制，精确豁免单方法；启动期无请求上下文，无法走租户过滤 | 把 skill 表加进租户白名单（否决：破坏请求期隔离） |
| D4 | builtin 绑定的外键冲突 | V15 删 `skill_id` 外键，完整性移交服务层 | 虚拟 ID 必然无对应行；bind/delete 已做显式校验 | 给 builtin 落全局行（否决：跨租户 + 白名单矛盾） |
| D5 | 审计失败策略 | best-effort（Recorder 内部 try/catch） | 审计是旁路，不能拖垮主对话 | 强一致写入（否决） |
| D6 | 工具线程上下文 | callAsync 内手动 set/restore ThreadLocal | Reactor 切线程后租户拦截器取不到 workspace_id | 全链路 Context 传参（改造面过大） |

## 7. 性能与扩展

- **每次对话重建 Toolkit**：绑定数通常 < 10，装配开销可忽略；换取"绑定改动即时生效"。
- **SkillRegistry** 为 `ConcurrentHashMap`，读写无锁竞争瓶颈。
- **扩展点**：MCP Skill 只需实现 `SkillExecutor` 并在启动/发现时 `register`，复用全部工具化与审计链路；`configOverride`（Agent 级参数覆盖）已在 `SkillInvocation` 预留 TODO。
- **审计查询**：`skill_call_log` 按 workspace_id 隔离，后续监控页按时间/技能聚合即可。

## 8. 测试要点

单测（JUnit 5 + Mockito，`mvn test` 全绿，40 例）：

- `UrlSafetyUtilTest`：环回/私网/链路本地/非法协议/缺失主机拦截，公网数字 IP 放行（全数字 IP，离线可跑）。
- `ApiSkillExecutorTest`：Descriptor 构建、坏 Schema 降级、缺 url / SSRF / 坏 config 的失败路径（不发起真实 HTTP）。
- `SkillServiceImplTest`：create 成功即注册、各类校验错误码、update 刷新 Registry、delete 绑定保护/注销、test 的 Registry 命中与 DB 兜底。
- `SkillAgentToolTest`：工具名清洗规则。
- 回归：修复 `ChunkServiceTest` 长期无法编译的问题（补 test 依赖 + 纠正与 `MIN_CHUNK_LENGTH_TO_EMBED=10` 设计冲突的断言）。

运行时验证（真实 API + Cookie 认证，见 Day 15 日志）：列表虚拟挂载、跨租户隔离、创建/校验/SSRF 拦截、测试调用全链路、更新、绑定（builtin + api）、重复绑定保护、toggle、删除保护与解绑删除、启动期从 DB 恢复 api Skill（跨重启）。

**待验证（依赖真实 LLM Key）**：ReAct 循环中的 function calling 实调 + `skill_call_log` 落库。
