# Skill 系统技术方案

> Phase 2 · 课题①（Skill 工具化）+ 课题②（Skill 中心后端）+ 课题③（Skill 调试器）+ 课题⑦（内容型 Skill + 导出/导入）+ 课题⑧（企业化升级：业务分类 + 搜索 + 双 Tab + 创建双轨制，2026-08-10）+ 课题⑨（Skill 中心 v2：技能广场 + 工种导航 + 技能包导入 + MCP 发布治理 + 动作型两阶段确认，2026-08-12）
> 状态：已交付（后端全链路 + 单元测试 + 运行时验证 + 前端 Skill 中心 v2 三页面）；真实 LLM function calling 联调待 Key。
> 课题⑧立项依据：6 平台竞品调研 `docs/research/2026-08-10-skill-platform-research.md`。
> 课题⑨立项依据：Skill 中心 v2 PRD `docs/prd.md` + 原型 `docs/ui-demo/`（面向中小企业全工种：市场/销售/客服/人事/财务/法务合规/行政/数据分析/IT集成/其他）。
> MCP 集成（课题④）以虚拟 Skill 接入本体系，详见 [06-mcp-integration.md](06-mcp-integration.md)。

## 1. 概述

Skill 系统负责 Agent 的"动手能力"：把知识库检索、HTTP 调用、代码执行、用户自建的 API 封装成统一的**可执行技能**，并在对话中由 LLM 通过 **function calling** 自主决定何时调用。

核心能力：

| 能力 | 说明 |
|------|------|
| 统一执行器抽象 | `SkillExecutor` 接口统一 builtin / api / prompt / mcp 四类技能 |
| 工具化（Phase 2 核心改造） | Skill 注册为 AgentScope `Toolkit` 原生工具，**替换 MVP 的提示词注入**，LLM 以标准 function call 调用 |
| 虚拟挂载 builtin | 内置 Skill 不落库，只存在于内存 `SkillRegistry`；列表/绑定时动态合并 |
| Skill 中心（API 模式） | 用户通过 REST API 创建 HTTP 封装 Skill：CRUD + 测试调用 + 启动/运行时动态注册。定位：存量系统对接的"工具接入"（IT/集成侧） |
| 内容型 Skill（课题⑦） | `type=prompt`：写指令内容（SOP/规范）发布为 Skill，**渐进式披露**——LLM 只见 name+description，function call 按需加载全文；零新运行时机制 |
| Skill 导出/导入（课题⑦） | 用户 Skill 导出为单个 JSON 定义（`agentone-skill` 格式），跨工作空间导入重建；导入走与创建相同的校验链路 |
| 业务分类 + 搜索（课题⑧） | `category` 字段（受控词表，V18 加列）+ 关键词搜索（名称/描述大小写不敏感）+ 类型筛选；DB 与虚拟挂载同一过滤口径 |
| 创建双轨制（课题⑧） | 内容型走模板库（6 模板预填，纯前端）；API 型走结构化表单（url/method/timeout/headers/参数表格）↔ 高级 JSON 双向切换 |
| 技能广场（课题⑨） | 发现视角 `GET /api/skills/plaza`：本空间 active 用户 Skill + builtin + **已发布** MCP 工具；工种词表导航 + 关键词搜索；官方 Skill 以 `source=official` 标识（少量内置演示） |
| 技能包导入（课题⑨） | `POST /api/skills/import-package`：整个文件夹（files+paths）或单个 `.zip`/`.md`；以 `SKILL.md` frontmatter 识别名称/描述/工种；落 `skill` + `skill_package_file`（scripts/resources 随包存储）；zip-slip 防护 |
| 脚本安全红线（课题⑨） | 技能包内脚本**仅存储与分发，服务端绝不执行**——无解释器、无沙箱、无执行入口；`skill_package_file.kind=script` 只供详情展示与导出 |
| 动作型两阶段确认（课题⑨） | `actionType` 技能广场调用先回草稿 + 一次性确认令牌（绑定技能 + 参数 SHA-256 摘要，5 分钟过期），用户确认后携带令牌才真实执行；MCP 工具默认动作型 |
| MCP 发布治理（课题⑨） | MCP 工具默认不进广场；IT 在 MCP 管理页按工具显式「发布到广场」（`mcp_tool_publish` 表）；未发布工具广场不可见、绑定被拦截（5016） |
| Skill 调试器（课题③） | 三步向导：列目标 → 参数预检（轻量 JSON Schema 校验 + 执行计划预览）→ 真实执行；上下文强制取登录态，调试写审计 |
| 调用链审计 | 每次 Skill 执行写 `skill_call_log`（best-effort，不影响主对话链路）；调试执行以 `agent_id='debugger'` 标记来源 |
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
   ├─ api：ApiSkillExecutor（skill 模块，DB 配置驱动，每 Skill 一实例）
   ├─ prompt：PromptSkillExecutor（skill 模块，课题⑦，无出站，返回指令内容）
   └─ mcp：McpSkillExecutor（skill 模块，连接发现时每工具一实例，见 06-mcp-integration.md）

用户 Skill（api / prompt）的实例构建统一走 UserSkillExecutors 工厂（课题⑦）：
   isUserType(type) = api || prompt；create(SkillDO, WebClient, ObjectMapper) 按 type 分派
   调用方：SkillServiceImpl（create/update/test 兜底）+ UserSkillBootstrap（启动加载）
```

## 2. 架构设计

### 2.1 装配流程（启动期 + 请求期）

```
启动期:
  SkillAutoRegisterConfig（ApplicationRunner）
      → 收集容器内全部 SkillExecutor Bean（3 个 builtin）→ SkillRegistry.register
  UserSkillBootstrap（ApplicationRunner，课题⑦起替代 ApiSkillBootstrap）
      → SkillMapper.selectAllActiveUserSkills()（@InterceptorIgnore 跳过租户过滤，type IN api/prompt）
      → 每行经 UserSkillExecutors.create 构建执行器 → SkillRegistry.register

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
- 定位（课题⑦澄清）：API 封装与 MCP 在"给 LLM 注册 HTTP 工具"上职责重叠，保留它是因为存量系统对接的**零对端改造**价值真实；对外定位降级为"工具接入（IT/集成侧）"，不与 MCP 争通用工具协议生态位。

### 3.5 PromptSkillExecutor（内容型执行器，课题⑦）

非 Spring Bean，DB 每行一个实例，构造入参 `(SkillDO, ObjectMapper)`。**渐进式披露**的执行侧实现。

- `execute` 只有两条路：解析 `config.content` 非空 → `SkillResult.success({name, content})`；无效（缺失/空白/坏 JSON/null config）→ 失败 SkillResult，**不抛异常**；
- **无出站请求**：没有 WebClient / 超时 / SSRF，内容原文返回，不做模板渲染（保持"指令资产原样进上下文"）；
- `getDescriptor`：`type="prompt"`，`inputSchema` 恒为 `EMPTY_SCHEMA = {"type":"object","properties":{}}`——以**无参工具**注册进 Toolkit，LLM 只凭 name+description 决定是否调用；`workspaceId` 照填（越权校验依赖）。

```java
// PromptSkillExecutor.execute —— 无出站请求，纯内容返回
JsonNode cfg = objectMapper.readTree(firstNonNull(skill.getConfig(), "{}"));
String content = cfg.path("content").asText("");
if (StringUtils.isBlank(content)) return SkillResult.failure("Skill 配置缺少 content", ...);
return SkillResult.success(Map.of("name", skill.getName(), "content", content), ...);
```

**为什么拆成独立类而非 ApiSkillExecutor 加分支**：两者安全边界不同（api 有 SSRF 面，prompt 没有），职责清晰，将来加脚本型也不互相污染。

### 3.6 Skill 导出 / 导入（课题⑦）

发布/分享的最小形态——单 JSON 文件流转，不做中心化市场。

- **export**（`GET /api/skills/{id}/export`）：仅用户 Skill（builtin/mcp 虚拟挂载无 DB 行 → 5006）；产出 `SkillExportVO`：`format="agentone-skill"`、`formatVersion=1`、name/type/description/config/inputSchema/outputSchema/version、`exportedAt` 服务端生成；
- **import**（`POST /api/skills/import`）：format 护栏（外来格式 5007）→ 同空间 **name+type** 重复拒绝（**5013**）→ `createInternal(dto, "imported")`；
- 导入产物 `source="imported"`，与 custom 区分；导入与创建**共用 createInternal 单一入口**，content/SSRF 校验无法绕过。

### 3.7 Skill 中心服务（SkillServiceImpl，课题⑦重构后）

| 操作 | 行为 | 错误码 |
|------|------|--------|
| create | `resolveType`（缺省 api）→ `validatePayload` 按 type 分支 → 落库 → `registry.register` | 5007 / 5008 |
| update | `requireUserSkill` → type 变更拒绝 → 同 create 校验 → updateById → 覆盖注册 | 5002 / 5006 / 5007 / 5008 |
| delete | `requireUserSkill` → 有绑定拒绝 → deleteById → `registry.unregister` | 5005 仍被绑定 |
| test | Registry 优先；不在则按 DB 行经工厂临时构建执行器直跑 | 5002 / 5006 |
| export | `requireUserSkill` → SkillExportVO（§3.6） | 5002 / 5006 |
| importSkill | format 护栏 → name+type 重复拒绝 → `createInternal(dto, "imported")` | 5007 / 5013 |

- `validatePayload` 按 type 分支：**prompt** 要求 config 合法 JSON 且 `content` 非空、强制 inputSchema/outputSchema 为 `{}`、**不走 SSRF**（无出站请求）；**api** 要求 config 含 url + `UrlSafetyUtil`（5008）+ schema 可解析。
- `resolveType`：dto.type 为 null/空白 → `api`（存量调用方向后兼容）；非 api/prompt → 5007。
- `requireUserSkill`（原 requireApiSkill 改名）：`selectById`（租户拦截器天然过滤跨工作空间）+ `UserSkillExecutors.isUserType` 校验，builtin/mcp 不可改。
- 写操作同步维护 Registry，**创建成功后 Agent 立即可绑定、对话可用**，无需重启。

### 3.8 UrlSafetyUtil（SSRF 防护）

只放行 http/https；解析主机后拒绝环回 / 私网（site-local）/ 链路本地 / 通配地址——即拦截 `127.0.0.1`、`169.254.169.254`（云元数据）、`10.*` / `192.168.*` 等。`HttpRequestSkill` 与 `ApiSkillExecutor` 共用。已知局限：存在 DNS 重绑定理论风险（检查与连接两次解析可能不一致），生产建议对解析出的地址直连（列为后续增强，见 improvements.md）。

### 3.9 Skill 调试器（SkillDebugServiceImpl，课题③；课题⑦补 prompt 分支）

三步向导对应三个端点，全部走 Registry 统一入口：

| 步骤 | 端点 | 行为 |
|------|------|------|
| 1 选目标 | `GET /api/skills/debug/targets` | Registry 描述符按 workspaceId 过滤（`null`=builtin 全局可见）+ 排序 |
| 2 预检 | `POST /api/skills/debug/preview` | `JsonSchemaLiteValidator` 校验参数 + 生成执行计划文本（**不落真实请求**） |
| 3 执行 | `POST /api/skills/debug/run` | 真实执行 + 写审计（`agent_id='debugger'`，traceId=`debug-{8位hex}`） |

**安全边界**（关键设计）：执行上下文 userId/workspaceId **强制取当前登录态**——`DebugRunDTO` 只开放 skillId/params/sessionId 三个字段，接口形态上消除跨租户注入面；`requireExecutor` 另按描述符 workspaceId 拦截越权（5004）。真实执行复用 executor 原路径，api Skill 的执行期 SSRF 二次校验等安全机制天然生效。

**执行计划预览**：api 类型读 DB config 还原 `HTTP {method} {url}（GET/DELETE 参数走 query，POST/PUT 参数走 JSON body）`；prompt 类型给"返回 Skill 指令内容（渐进式披露，不发起外部请求）"；mcp/builtin 给结构化描述。坏 config 降级为提示文案，不阻断。

**JsonSchemaLiteValidator**（`core/`）：只覆盖本系统 inputSchema 实际用到的子集——required（兼容 `List` 与 `Object[]` 两形态）+ 基本 type（string/number/integer/boolean/object/array）；null 值跳过类型检查、未知字段/未知类型放行（不误杀）。不引入完整 JSON Schema 校验库：本场景 schema 均为系统内简单 object schema，且校验库传递依赖在离线构建环境不确定。

### 3.10 列表搜索与筛选（AgentSkillServiceImpl.listSkills，课题⑧）

列表数据来自两处：DB 分页查询（用户 Skill：api/prompt）+ 虚拟挂载（Registry 里的 builtin 全局 + 本空间 MCP 工具）。课题⑧让两路套用**同一套过滤条件**：

```
listSkills(workspaceId, keyword, category, type, page, size)
  ├─ DB 路（MyBatis-Plus）:
  │    keyword → LOWER(name) LIKE {0} OR LOWER(description) LIKE {0}（{0} 占位符参数化，无注入）
  │    category/type → eq 精确匹配；按 installedAt 倒序分页
  ├─ 虚拟路（virtualVOs 内存过滤，同一 keyword/category/type 条件）:
  │    builtin（workspaceId=null 全局）+ mcp（descriptor.workspaceId == 当前空间）
  │    只合并 type ∈ {builtin, mcp}——api 虽也在 Registry 但已由 DB 分页返回且跨租户注册，不可重复合并
  │    keyword 用 name/description 的 toLowerCase().contains()，与 DB 的大小写不敏感口径一致
  └─ 合并：total 始终 += 虚拟条数（翻页总数正确）；仅第一页把虚拟项置顶插入 records
```

**分类的虚拟挂载侧**：`virtualVOs` 把描述符的 category 透出到 VO；builtin 在注册时由各自类声明（`KnowledgeSearchSkill`→办公效率，`HttpRequestSkill`/`CodeExecuteSkill`→IT集成，走 `SkillCategories` 常量）；MCP 工具在 `McpSkillExecutor` 统一归 IT集成。

**前端（SkillCenterPage.vue，课题⑧重写）**：
- 双 Tab：技能浏览（搜索框 300ms 防抖 + 类型下拉 + 分类 chip 行 + 卡片网格，两种空态）／技能调试（三步向导内联）；浏览卡片"调试"按钮切 Tab 并预选目标；
- 创建双轨制：内容型 = 模板库 6 模板（`TEMPLATES` 常量，选中预填 name 建议分类与 Markdown 骨架，纯前端零后端改动）；API 型 = 结构化表单 ↔ 高级模式（config JSON）双向解析（`parseApiIntoForm`/`buildApiConfig`/`buildInputSchema`），结构化字段为 url/method/timeout/headers 键值行/参数表格（name/type/description/required → 生成 inputSchema）；outputSchema 前端不再呈现（A3-2 部分）。

### 3.11 技能广场（AgentSkillServiceImpl.listPlaza，课题⑨）

课题⑧的 `listSkills` 是**管理视角**（我的技能，分页 + 类型筛选）；课题⑨新增**发现视角**广场接口，两者数据同源（DB + 虚拟挂载）但可见性规则不同：

```
listPlaza(workspaceId, keyword, category)
  ├─ DB 路：本空间 status=active 的用户 Skill（自建/导入/官方）
  │         keyword → LOWER(name/description) LIKE；category → eq；按 installedAt 倒序；不分页
  └─ 虚拟路：virtualVOs(..., plazaOnly=true, publishedIds)
             builtin 全部可见；MCP 工具仅保留 publishedIds（join mcp_tool_publish）中的
```

- **广场与管理列表的口径差异**：管理列表看全部（含 disabled、含未发布 MCP 工具——管理页要能治理它们）；广场只看"可被业务用户使用"的子集。
- **MCP 发布过滤**：`publishedMcpSkillIds(workspaceId)` 查 `mcp_tool_publish` 表得到已发布虚拟 Skill ID 集合，`virtualVOs` 的 `plazaOnly` 分支按集合过滤。
- 分类词表 v2 起按**工种**：市场/销售/客服/人事/财务/法务合规/行政/数据分析/IT集成/其他（V19 迁移把旧业务场景词表就近映射）；builtin 全部归 IT集成（`SkillCategories.IT`）。
- `source=official` 为官方演示技能预留（前端「官方精选」区块过滤该值）；本期未预置官方技能数据，区块自动隐藏。

### 3.12 技能包导入（SkillServiceImpl.importPackage，课题⑨）

对接 Claude Skill / 外包交付物的标准形态：一个文件夹（或 zip），`SKILL.md` 为入口，可携带 `scripts/`、资源文件。

```
importPackage(file | files+paths)
  ├─ 入参归一：file 单文件（.zip → parseZip 内存解压；.md → 单文件包）
  │            files+paths = 文件夹导入（paths.size 必须等于 files.size，否则 5014）
  ├─ 逐文件 normalizePackagePath：去空路径、拒绝绝对路径与 ".." 段（zip-slip 防护）
  ├─ 限额：≤ MAX_PACKAGE_FILES 个文件、单文件 ≤ 10MB（5014）
  ├─ 定位 SKILL.md（缺失 5014）→ parseFrontmatter：
  │    name（缺失 5014）/ description / category（不在工种词表内则归"其他"）
  │    body（frontmatter 之后的正文）作为 prompt 技能的 config.content
  ├─ 同空间同名拒绝（5013，与 JSON 导入口径一致）
  ├─ createInternal(dto, "imported")：type=prompt 走统一校验入口
  └─ 逐文件落 skill_package_file：kind = script / resource / doc（SKILL.md）
     文本文件存 content；二进制资源 content=NULL 仅存元信息
```

**安全红线（不可妥协）**：包内脚本**只存储、不执行**。服务端没有任何脚本解释器/执行入口，`kind=script` 的文件与资源文件同等对待——详情抽屉展示、随导出分发，到此为止。"导入即可运行第三方脚本"需要完整沙箱体系（权限/配额/审计），不在本期范围，绝不用"先跑起来再说"的临时方案替代。

### 3.13 动作型技能两阶段执行确认（invoke + ConfirmTokenStore，课题⑨）

广场「试一试」直连执行器（`POST /api/skills/{id}/invoke`），动作型技能（发送/写入/删除类副作用）必须经用户二次确认：

```
阶段 1（首次调用，无 confirmToken）:
  descriptor.isActionType() → 返回 {confirmRequired:true, confirmToken, draftParams}，绝不执行
  ConfirmTokenStore.issue(skillId, params)：UUID 令牌 ← (skillId, SHA-256(排序后参数), now+5min)

阶段 2（带 confirmToken）:
  ConfirmTokenStore.consume：令牌存在 + 未过期 + skillId 匹配 + 参数摘要一致 → 一次性消费放行
  任一不满足 → 5015「确认令牌无效或已过期，请重新生成草稿并确认」
  放行后走 executor.execute 原路径（SSRF 等安全机制天然生效）→ 写审计（agentId='plaza'）
```

- **令牌绑定参数摘要**：拿到令牌后篡改参数 → 摘要不匹配 → 拒绝。摘要用 `TreeMap` 排序顶层 key 后序列化再 SHA-256，同一份参数摘要稳定。
- **内存态即够**（`ConcurrentHashMap`）：确认是秒级交互，重启后重新生成草稿无业务损失；签发/消费时惰性清理过期项，无后台线程。
- **actionType 判定**：用户 Skill（api/prompt）读 `config.actionType`（`UserSkillExecutors.isActionType`）；MCP 工具默认 `true`（SDK 无注解可读，副作用保守处理）；builtin 查询类为 `false`。
- 非动作型技能直接执行，响应 `confirmRequired=false` + 结果字段，前端无需二次交互。

### 3.14 MCP 发布治理（课题⑨）

MCP 工具是 IT 接入的"原材料"，不等于业务用户可直接使用的技能——未加治理时，连上一个带 `delete_*` 工具的 Server 就意味着全广场可见可调用。v2 的治理：

- `mcp_tool_publish(workspace_id, server_id, tool_name, published)` 独立存发布态（MCP 工具是虚拟 Skill，不落 `skill` 表）；**默认 false**；
- `PUT /api/mcp-servers/{id}/tools/{toolName}/publish`（`PublishToolDTO{published}`）按工具开关；工具列表 VO 带 `published` 供前端渲染开关；
- 广场列表：未发布不可见（§3.11）；**绑定拦截**：`bind` 对 `mcp-` 前缀 skillId 校验发布态，未发布 → 5016；
- 与 `setStatus` 语义对齐：我的技能页对 MCP 虚拟项 toggle = 发布/撤回（同一入口两种形态）。

### 3.15 技能启停（setStatus，课题⑨）

我的技能页 toggle 的统一入口 `PUT /api/skills/{id}/status?enabled=`：

- 用户 Skill：`status` 切 active/disabled，**同步 Registry**——停用即 `unregister`（Agent 对话侧装配不到执行器，立即可停），启用重建执行器重新 `register`；
- MCP 虚拟项：转发 `setMcpToolPublished`（启停 = 发布/撤回）；
- builtin：系统托管拒绝（5006）。

### 3.16 前端：Skill 中心 v2 三页面（课题⑨）

- **SkillCenterPage.vue（重写）**：双 Tab 技能广场/我的技能。广场 = 工种导航行（10 工种 chip + 全部）+ 搜索（300ms 防抖）+ 官方精选区块（`source=official` 且无搜索/全部工种时）+ 卡片网格（工种徽章、动作型/查询型标记、调用次数）+ 详情抽屉（说明/参数 Schema/包文件树/试一试）；「试一试」对动作型走两阶段（先展示草稿参数，确认后带 token 二次请求）。我的技能 = 管理表格（启停 toggle/删除/调试行内动作），调试由独立 Tab 降级为行内动作。
- **SkillCreatePage.vue（新建，`/skills/create`）**：五入口向导——模板库（14 模板按工种预填）/空白创建/内容导入（文件夹、.zip、单 .md、粘贴 SKILL.md 文本四种形态，真实调用 import-package 或解析后走 create）/脚本包创建（表单生成 SKILL.md + 附加脚本资源文件，拼 files+paths 调 import-package）/API 接入（结构化表单 → config + inputSchema）。步骤 1 选入口 → 2 填内容 → 3 发布成功。
- **McpServerPage.vue**：工具弹窗增加 per-tool「发布到广场」开关（调 publish 接口）+ 动作型/查询型徽章。

## 4. 数据库设计

### 4.1 skill（存用户创建的 api / prompt / imported，V2 建表）

`id(PK) / workspace_id / name / type / source / category / description / input_schema(JSONB) / output_schema(JSONB) / config(JSONB) / version / status / installed_at`。受租户拦截器保护。
- `type`：`api`（HTTP 封装）/ `prompt`（内容型，课题⑦）；`source`：`custom`（创建）/ `imported`（导入，含技能包导入）/ `official`（官方演示，课题⑨预留）/ `agentone`（保留）。
- `category`（课题⑧，V18 加列 `VARCHAR(50) NOT NULL DEFAULT '其他'`）：**课题⑨起语义改为「工种」**，受控词表由前端选择器维护（市场/销售/客服/人事/财务/法务合规/行政/数据分析/IT集成/其他）；V19 迁移把旧业务场景词表就近映射（风控合规→法务合规、人力资源→人事、客服运营→客服、办公效率→其他）。后端 `SkillCategories` 只定义系统侧固定值（DEFAULT=其他 / IT=IT集成），**不做枚举强校验**——词表可演进，避免前后端双份维护。
- prompt 类型的 config 结构：`{content}`（技能包导入时 = SKILL.md 去 frontmatter 的正文）；input_schema/output_schema 强制 `{}`；config 可选 `actionType`（课题⑨）。

### 4.4 skill_package_file（V19 建表，课题⑨）

`id(PK) / skill_id(FK→skill.id) / path(包内相对路径，VARCHAR(500)) / kind(script/resource/doc) / size / content(TEXT，二进制为 NULL) / created_at`；`UNIQUE(skill_id, path)` + `skill_id` 索引。
一个技能包一对多：`SKILL.md`（kind=doc，必需）+ 脚本 + 资源文件。**脚本仅存储不执行**（§3.12 安全红线）。

### 4.5 mcp_tool_publish（V19 建表，课题⑨）

`id(PK) / workspace_id(FK→workspace.id) / server_id / tool_name(VARCHAR(200)) / published(默认 false) / updated_at`；`UNIQUE(workspace_id, server_id, tool_name)` + workspace 索引。
MCP 工具是虚拟 Skill（不落 `skill` 表），发布状态独立存储；广场可见性与绑定校验依赖此表。

### 4.2 agent_skill_binding（V2 建表，V14 补 workspace_id）

`id / agent_id / skill_id / skill_version / config_override(JSONB) / enabled / created_at`。
**V15：移除 `skill_id → skill.id` 外键**（builtin 虚拟 ID 在 skill 表无对应行），`agent_id` 外键保留。
**V17：`skill_id` 扩至 VARCHAR(200)**（MCP 虚拟 ID `mcp-{32hex}-{toolName}` 超原 UUID 长度，详见 06-mcp-integration.md）。

### 4.3 skill_call_log（V4 建表，课题①启用写入）

`id / workspace_id / agent_id / skill_id / session_id / trace_id / input_params(JSONB) / output_result(JSONB) / duration_ms / token_count / status / error_message / created_at`。
受租户拦截器保护（不在 IGNORE_TABLES）；写入由 `SkillCallLogRecorder` best-effort 完成（异常仅告警，不阻断对话）。

## 5. API 设计

统一前缀 `/api/skills`，Cookie 认证（S7：token 仅走 HttpOnly Cookie，响应体不下发）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skills?page&size&keyword&category&type` | 列表：keyword 名称/描述大小写不敏感模糊匹配，category/type 精确筛选；第一页置顶合并虚拟项（虚拟条数始终计入 total）；VO 带 category |
| POST | `/api/skills` | 创建用户 Skill（body: SkillDTO，`type` 缺省 api / prompt，`category` 缺省"其他"；分支校验见 §3.7） |
| PUT | `/api/skills/{id}` | 更新用户 Skill（禁止 type 变更，category 可改） |
| DELETE | `/api/skills/{id}` | 删除用户 Skill（有绑定拒绝 5005） |
| POST | `/api/skills/{id}/test` | 测试调用（body: `{params}`，返回真实 SkillResult） |
| GET | `/api/skills/{id}/export` | 导出 Skill 定义为 JSON（课题⑦，仅用户 Skill，builtin/mcp 5006） |
| POST | `/api/skills/import` | 导入 Skill 定义（课题⑦，同名+同 type 拒绝 5013，source 标记 imported） |
| GET | `/api/skills/plaza?q&cat` | 技能广场（课题⑨）：active 用户 Skill + builtin + 已发布 MCP 工具；不分页 |
| POST | `/api/skills/import-package` | 导入技能包（课题⑨，multipart）：`file`=单 .zip/.md 或 `files[]`+`paths[]`=文件夹；SKILL.md 必需，脚本仅存储不执行；同名 5013 / 包非法 5014 |
| GET | `/api/skills/{id}/package-files` | 技能包文件树（课题⑨）：path/kind/size/content |
| PUT | `/api/skills/{id}/status?enabled=` | 启停（课题⑨）：用户 Skill 切 status 并同步 Registry；MCP 虚拟项 = 发布/撤回；builtin 5006 |
| POST | `/api/skills/{id}/invoke` | 广场调用（课题⑨）：`{params?, confirmToken?}`；动作型两阶段确认，写审计（agentId=plaza）；令牌无效 5015 |
| GET | `/api/skills/debug/targets` | 调试第 1 步：当前空间可调试 Skill（builtin/api/mcp） |
| POST | `/api/skills/debug/preview` | 调试第 2 步：参数预检 + 执行计划（body: `{skillId, params}`，不真实调用） |
| POST | `/api/skills/debug/run` | 调试第 3 步：真实执行（body: `{skillId, params, sessionId?}`，写调试审计） |
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
| 5013 | 导入时同空间已存在同名+同 type 的 Skill（课题⑦） |
| 5014 | 技能包非法（缺 SKILL.md / frontmatter 无 name / zip 解析失败 / 路径非法 / 超大小或数量上限 / files 与 paths 数量不一致）（课题⑨） |
| 5015 | 动作型技能确认令牌无效或已过期（课题⑨） |
| 5016 | MCP 工具未发布到广场，拒绝绑定（课题⑨） |

MCP 侧新增端点（课题⑨，详见 06-mcp-integration.md）：

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/mcp-servers/{id}/tools/{toolName}/publish` | 工具级「发布到广场」开关（body `{published}`，默认关闭） |

## 6. 关键技术决策

| # | 决策 | 选择 | 理由 | 备选方案 |
|---|------|------|------|---------|
| D1 | Skill 如何被 LLM 调用 | AgentScope Toolkit 原生 function calling | 标准工具协议，LLM 自主决策调用时机与参数；提示词注入无法结构化传参、不可审计 | 提示词注入（MVP 做法，已废弃） |
| D2 | builtin 存哪 | 虚拟挂载（只进 Registry，不落库） | 避免按工作空间复制同步 + 单列主键冲突 | 每工作空间落一行（否决） |
| D3 | 启动期如何加载 api Skill | `@InterceptorIgnore` 专用 Mapper 方法 | MP 原生机制，精确豁免单方法；启动期无请求上下文，无法走租户过滤 | 把 skill 表加进租户白名单（否决：破坏请求期隔离） |
| D4 | builtin 绑定的外键冲突 | V15 删 `skill_id` 外键，完整性移交服务层 | 虚拟 ID 必然无对应行；bind/delete 已做显式校验 | 给 builtin 落全局行（否决：跨租户 + 白名单矛盾） |
| D5 | 审计失败策略 | best-effort（Recorder 内部 try/catch） | 审计是旁路，不能拖垮主对话 | 强一致写入（否决） |
| D6 | 工具线程上下文 | callAsync 内手动 set/restore ThreadLocal | Reactor 切线程后租户拦截器取不到 workspace_id | 全链路 Context 传参（改造面过大） |
| D7 | 调试器执行上下文 | 强制取登录态，DTO 只开放 sessionId | 从接口形态上消除跨租户注入面；安全属性不依赖实现自觉 | 允许传 userId/workspaceId 做"模拟执行"（否决：权限提升面） |
| D8 | 参数预检实现 | 自研轻量子集校验器（required + 基本 type） | schema 均为系统内简单 object schema；完整校验库传递依赖在离线构建不确定 | 引入完整 JSON Schema 校验库（否决：依赖面过大） |
| D9 | 内容型 Skill 如何被加载 | 渐进式披露：无参 function tool，LLM 按需 call 加载全文 | token 经济 + 无关指令不干扰；零新运行时机制（复用工具化全链路） | 全量拼进 system prompt（否决：token 爆炸 + 干扰） |
| D10 | 内容型与 API 执行器关系 | 拆独立 PromptSkillExecutor + UserSkillExecutors 工厂分派 | 安全边界不同（api 有 SSRF 面、prompt 无出站）；职责清晰可扩展 | ApiSkillExecutor 加 type 分支（否决：边界混淆） |
| D11 | Skill 发布/分享形态 | 导出/导入单 JSON 文件（agentone-skill 格式） | 最小可用；导入走 createInternal 单一校验入口，不绕过 content/SSRF | 中心化 Skill 市场（后续课题）/ 私有 registry（过早） |
| D12 | Skill 组织维度（课题⑧） | 按**业务场景**分类（受控词表），不按技术形态 | 企业用户（业务专家）只认"信贷审批规范/客诉流程"，不认"API 封装"；竞品调研结论（docs/research/2026-08-10-skill-platform-research.md） | 自由标签（企业不可管控，否决）/ 按技术形态分（Dify 式，与定位冲突） |
| D13 | 分类词表谁维护（课题⑧） | 前端选择器单点维护，后端不做强枚举校验（只留 SkillCategories 系统固定值） | 词表演进只改一处；后端强枚举会把"加一个分类"变成前后端双改 + 迁移 | 后端 enum 强校验（双份维护，否决） |
| D14 | 搜索/筛选与虚拟挂载的关系（课题⑧） | DB 与虚拟挂载套用同一过滤条件；虚拟条数始终计入 total，仅第一页置顶合并 | 翻页总数正确；两路口径一致（keyword 均大小写不敏感），避免"搜得到 DB 项搜不到内置项"的割裂 | 虚拟项不参与过滤（筛选后内置项残留，体验错误） |
| D15 | 创建表单形态（课题⑧） | 双轨：内容型模板库（纯前端预填）+ API 型结构化表单↔JSON 双向解析 | 模板库零后端改动即降低业务用户冷启动门槛；结构化表单消掉裸 JSON（A3-1），高级模式兜底复杂场景 | 仅裸 JSON（原状，否决）/ 仅表单无 JSON 兜底（表达力受限） |
| D16 | 技能组织维度 v2（课题⑨） | 分类词表从「业务场景」改为「**工种**」（10 类），广场按工种导航 | 面向中小企业全工种：业务用户按"我是哪个岗位"找技能，比按场景找直觉成本低；PRD/原型评审确认 | 沿用业务场景词表（课题⑧，与 v2 定位不符）/ 自由标签（不可治理） |
| D17 | 广场与管理列表的关系（课题⑨） | 同一数据源两个视角：plaza（发现，active + 已发布 MCP）/ list（管理，全量含 disabled 与未发布） | 业务用户看到的 = 可用的；IT 看到的 = 可治理的；一套虚拟挂载逻辑两种过滤口径 | 只留一个列表接口（治理项混入业务视野，否决） |
| D18 | 技能包脚本的处理（课题⑨） | **仅存储与分发，服务端绝不执行** | 执行第三方脚本需沙箱体系（权限/配额/审计/隔离），本期不具备；安全红线不做折中 | 导入即执行（否决：不可控代码执行面）/ 阻塞到沙箱就绪（否决：导入价值先兑现） |
| D19 | MCP 工具进广场的方式（课题⑨） | 默认不发布，IT 按工具显式发布（mcp_tool_publish 表 + 绑定拦截 5016） | MCP Server 是 IT 视角的原材料，连上 ≠ 业务可用；副作用工具（delete_* 类）未经确认不应暴露 | 连接即全部可见（治理缺失，否决）/ Server 级整体发布（粒度太粗，单工具风险连坐） |
| D20 | 动作型确认令牌存储（课题⑨） | 内存 ConcurrentHashMap：令牌绑定 skillId + 参数 SHA-256 摘要，5 分钟 TTL，一次性消费，惰性清理 | 确认是秒级交互，重启重新生成草稿无损失；免引入 Redis 依赖与后台清理线程 | Redis 存储（多实例共享有价值，但当前单机部署过度设计，列为扩展项）/ DB 持久化（否决：高频短命数据） |

## 7. 性能与扩展

- **每次对话重建 Toolkit**：绑定数通常 < 10，装配开销可忽略；换取"绑定改动即时生效"。
- **SkillRegistry** 为 `ConcurrentHashMap`，读写无锁竞争瓶颈。
- **扩展点（已兑现）**：课题④ MCP 工具即按此路径接入——`McpSkillExecutor implements SkillExecutor` + 连接发现时 `register`，零改动复用工具化/绑定/审计/调试全链路（见 06-mcp-integration.md）；`configOverride`（Agent 级参数覆盖）仍在 `SkillInvocation` 预留 TODO。
- **审计查询**：`skill_call_log` 按 workspace_id 隔离，后续监控页按时间/技能聚合即可。

## 8. 测试要点

单测（JUnit 5 + Mockito，`mvn test` 全绿）：

课题①②（40 例）：
- `UrlSafetyUtilTest`：环回/私网/链路本地/非法协议/缺失主机拦截，公网数字 IP 放行（全数字 IP，离线可跑）。
- `ApiSkillExecutorTest`：Descriptor 构建、坏 Schema 降级、缺 url / SSRF / 坏 config 的失败路径（不发起真实 HTTP）。
- `SkillServiceImplTest`：create 成功即注册、各类校验错误码、update 刷新 Registry、delete 绑定保护/注销、test 的 Registry 命中与 DB 兜底。
- `SkillAgentToolTest`：工具名清洗规则。
- 回归：修复 `ChunkServiceTest` 长期无法编译的问题（补 test 依赖 + 纠正与 `MIN_CHUNK_LENGTH_TO_EMBED=10` 设计冲突的断言）。

课题③（24 例）：
- `JsonSchemaLiteValidatorTest`（13）：required 缺失/类型错/多错误汇总/null 跳过/未知字段放行/数组形态 required/未知类型不拦截。
- `SkillDebugServiceImplTest`（11）：目标列表租户过滤、preview 校验分支、run 写审计与跨租户 5004。

课题⑦（20 例）：
- `PromptSkillExecutorTest`（7）：内容返回、content 缺失/空白/null config/坏 JSON 失败路径、描述符（空 schema + workspaceId）、disabled 标志。
- `SkillServiceImplTest`（+13）：prompt 创建（schema 强制 `{}`、注册 PromptSkillExecutor）、缺 content 5007、未知 type 5007、type 变更拒绝、prompt 更新与 DB 兜底 test、导出全字段、builtin 导出 5006、导入 source=imported、同名 5013、外来格式 5007、导入缺 content 5007。

课题⑧（9 例）：
- `AgentSkillServiceImplTest`（新建，5）：listSkills 无过滤第一页合并虚拟项、keyword 命中名称/描述（大小写不敏感）、category 内存过滤、type 过滤、第二页虚拟项计入 total 但不进 records。
- `SkillServiceImplTest`（+4）：create 带 category 持久化、空白 category 缺省"其他"、update 变更 category、import 携带 category 持久化。

课题⑨（Skill 中心 v2，新增/扩展）：
- `ConfirmTokenStoreTest`（新建，6）：签发-消费同技能同参通过、二次消费拒绝、参数篡改拒绝、错技能拒绝、null/空白/未知令牌拒绝、参数摘要与 key 顺序无关。
- `SkillServiceImplTest`（扩展至 49）：importPackage（SKILL.md frontmatter 解析、同名 5013、缺 SKILL.md 5014、zip-slip 路径拒绝、files/paths 数量不一致 5014、脚本落 kind=script 且不执行）、invoke 两阶段（动作型首调只回草稿不执行、带令牌真实执行、令牌无效 5015、非动作型直接执行、写 plaza 审计）、setStatus（用户 Skill 启停同步 Registry、MCP 转发发布、builtin 5006）。
- `McpServerServiceImplTest`（扩展至 21）：publishTool 开关持久化、工具列表带 published/actionType。
- `AgentSkillServiceImplTest`（扩展至 9）：listPlaza 过滤未发布 MCP 工具、bind 未发布 MCP 工具拒绝 5016。

运行时验证（真实 API + Cookie 认证，见 Day 15 / Day 16 日志）：列表虚拟挂载、跨租户隔离、创建/校验/SSRF 拦截、测试调用全链路、更新、绑定（builtin + api + mcp）、重复绑定保护、toggle、删除保护与解绑删除、启动期从 DB 恢复用户 Skill（跨重启）；课题③补充：builtin/api/mcp 三类调试执行落审计（agentId=debugger）、preview 缺必填拦截、跨空间调试 5004；课题⑦补充（Day 16，14 项矩阵）：prompt 创建 schema 强制 `{}`、三分支 5007、test 返回全文（0ms 无出站）、调试 preview 计划文案、导出全字段、同名导入 5013、删除后重导入 source=imported、绑定 skillType=prompt、type 变更 5007、带绑定删除 5005、启动恢复日志。课题⑧补充（Day 16 晚）：category 创建/更新/导出/导入持久化、keyword/category/type 三过滤命中、本空间新建并连接 MCP Server 后工具归类 IT集成且跨租户工具不串入本空间列表、keyword 命中 MCP 工具名、调试目标列表带分类、前端 build 通过。课题⑨（Day 17）：后端全量 154 单测复跑全绿；前端 `npm run build` + `vue-tsc` 类型检查通过，Skill 中心 v2 三页面浏览器实测（广场/我的技能切换、工种过滤、详情抽屉、创建五入口向导、导入粘贴解析、发布开关交互）；新端点（plaza/import-package/invoke/status/publish）的带库运行时冒烟待 Docker 启动后执行。

**待验证（依赖真实 LLM Key）**：ReAct 循环中的 function calling 实调 + `skill_call_log` 落库。
**待验证（依赖 Docker 启动）**：课题⑨新端点运行时冒烟（V19 迁移落库 + plaza 可见性 + 技能包导入往返 + 动作型两阶段 + MCP 发布/绑定 5016）。
