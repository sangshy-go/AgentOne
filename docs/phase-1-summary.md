# AgentOne Phase 1 综合技术文档

> **项目**: AgentOne（灵一）—— 开源 AI Agent 中台
> **阶段**: Phase 1 - MVP
> **开发周期**: Day 1 ~ Day 13（2026-07-07 至 2026-07-21）
> **文档性质**: 面向初学者的完整技术说明，涵盖功能介绍、技术原理、实现细节、踩坑记录与解决方案
> **阅读建议**: 不需要 AI 开发经验。文中所有专业概念均附有解释。建议按顺序阅读，也可按目录跳转。

---

## 目录

- [一、项目是什么](#一项目是什么)
- [二、技术栈总览](#二技术栈总览)
  - [核心框架选型详解：AgentScope 与 Spring AI](#核心框架选型详解agentscope-与-spring-ai)
- [三、系统架构](#三系统架构)
- [四、功能模块详解](#四功能模块详解)
  - [4.1 用户认证与多租户隔离](#41-用户认证与多租户隔离)
  - [4.2 Agent 管理（六维配置 + 状态机）](#42-agent-管理六维配置--状态机)
  - [4.3 对话引擎（ReAct + SSE 流式）](#43-对话引擎react--sse-流式)
  - [4.4 知识库 RAG（核心重点）](#44-知识库-rag核心重点)
  - [4.5 模型管理（供应商 + 模型两层结构）](#45-模型管理供应商--模型两层结构)
  - [4.6 Skill 技能系统](#46-skill-技能系统)
  - [4.7 API Key 与开放接口](#47-api-key-与开放接口)
- [五、前端界面](#五前端界面)
- [六、数据库设计](#六数据库设计)
- [七、部署方案（Docker 一键部署）](#七部署方案docker-一键部署)
- [八、遇到的问题与解决方案（踩坑全记录）](#八遇到的问题与解决方案踩坑全记录)
- [九、关键技术决策与权衡](#九关键技术决策与权衡)
- [十、当前局限与 Phase 2 展望](#十当前局限与-phase-2-展望)
- [十一、概念词汇表](#十一概念词汇表)
- [十二、代码阅读指南](#十二代码阅读指南)
- [附录：API 接口速查](#附录api-接口速查)

---

## 一、项目是什么

### 1.1 定位

AgentOne（灵一）是一个**开源的 AI Agent 中台**——一个让你**在浏览器里完成所有操作**就能创建、配置、测试和部署 AI 智能体的完整平台。

不需要写一行代码。不需要理解 Transformer 架构。你只需要：写一段 Markdown 告诉 AI「你是谁」，上传几份文档告诉它「你应该知道什么」，选一个模型告诉它「用什么大脑思考」——一个可用的 AI Agent 就上线了。

它不是一个聊天机器人 Demo，而是一个**面向团队和生产环境的中台系统**：多租户隔离、API 接入、状态机生命周期管理、SSE 实时流式对话、Docker 一键部署，这些生产级能力开箱即用。

### 1.2 它能做什么（能力全景）

#### 可视化 Agent 工厂

在网页上创建和管理 AI Agent，全程零代码：

- **六维配置**：基础信息、人格指令（AGENTS.md）、模型策略、记忆配置、能力绑定、高级参数——六个 Tab 覆盖 Agent 的所有配置
- **Markdown 人设**：用一篇 Markdown 文档定义 Agent 的性格、行为准则、回答风格。支持 `{{user_name}}`、`{{date}}` 等运行时变量，让每个用户看到个性化回复
- **生命周期管理**：草稿 → 测试 → 发布 → 停用，严格的状态机保证只有经过测试的 Agent 才能上线。已发布的 Agent 不能直接改，必须先退回草稿
- **一键复制**：看到一个好用的 Agent 配置？复制一份，改改人设就是新场景

#### 实时流式对话

不是「发送 → 转圈等待 → 一次性显示」的原始体验：

- **逐字输出**：AI 的回复像打字一样实时流出（SSE 流式推送），不需要等完整生成
- **思考过程可见**：可以看到 AI 在想什么（「用户问的是退款政策，我需要检索知识库……」），展开/折叠自由切换
- **Markdown 渲染**：AI 回复中的标题、列表、代码块、表格全部正确渲染
- **多会话管理**：同一个 Agent 可以开多个独立会话，历史消息持久化存储

#### 企业知识库（RAG）

让 AI 不再只靠通用知识「猜」，而是**先查资料再回答**：

- **多格式支持**：PDF、Word、Markdown、TXT、CSV、HTML，拖拽上传即自动处理
- **全自动流水线**：上传 → 文档解析 → 智能分块 → 向量化 → 入库，全程异步，无需人工干预
- **多知识库绑定**：一个 Agent 可以同时绑定多个知识库（如产品手册 + 内部规范 + FAQ），每个知识库独立配置检索参数
- **检索参数可调**：Top-K（召回数量）和相似度阈值在前端直接调整，不同场景不同策略

#### 多模型、多供应商

不锁定任何一个 LLM 厂商：

- **供应商管理**：添加任意 OpenAI 兼容协议的供应商（通义千问、DeepSeek、Moonshot、OpenAI、本地部署的 Ollama……），填入 API Key 和地址即可
- **模型两层结构**：一个供应商下挂多个模型（Chat 对话用、Embedding 向量化用），按需选择
- **一键连通性检测**：配置完点「测试」，立刻知道 API Key 和地址是否正确
- **按 Agent 选模型**：不同 Agent 可以用不同的模型——客服用便宜快速的，分析用能力强的

#### API 接入（让 Agent 走出网页）

Agent 不只是在管理后台里对话，它可以被**任何程序调用**：

- **API Key 管理**：创建 Key（仅显示一次明文，数据库存哈希）、限制可调用的 Agent、设置每日调用上限
- **标准接口**：`POST /v1/chat`（同步）和 `POST /v1/chat/stream`（流式 SSE），与主流 LLM API 风格一致
- **健康检查**：`GET /v1/health` 无需认证，可接入监控系统探活
- 这意味着你可以把 Agent 接入自己的网站、App、企业微信机器人、自动化流程……

#### 团队协作（多租户）

- **工作空间隔离**：每个团队一个独立空间，数据完全隔离——A 团队看不到 B 团队的 Agent 和知识库
- **空间切换**：一个人可以属于多个工作空间，一键切换
- **角色管理**：owner / member 两级权限（Phase 2 将扩展为 4 级 RBAC）

#### 一键部署

```bash
git clone <repo> && cd agentone && docker compose up -d --build
# 打开浏览器，注册账号，开始创建你的第一个 Agent
```

PostgreSQL + Redis + 后端 + 前端，四个容器全部自动编排。Flyway 自动建表，不需要手动执行任何 SQL。

### 1.3 典型使用场景

| 场景 | 怎么用 |
|------|--------|
| **智能客服** | 上传产品手册和 FAQ 到知识库，创建一个「客服助手」Agent，绑定知识库，设置礼貌专业的人设 → 客户提问时 AI 先查手册再回答，不瞎编 |
| **内部知识问答** | 把公司制度、流程规范、技术文档扔进知识库，新员工直接问 AI：「请假流程是什么？」「报销标准是多少？」 |
| **文档分析助手** | 上传合同、报告，让 Agent 基于文档内容回答问题、提取关键信息 |
| **API 集成** | 生成 API Key，在自己的系统里调用 Agent 对话接口，把 AI 能力嵌入现有产品 |
| **多场景 Agent 矩阵** | 同一个知识库，不同的 Agent 人设：一个严肃合规、一个亲切活泼，服务不同渠道 |

### 1.4 从注册到对话：5 分钟上手

```
① 注册账号（邮箱 + 密码，自动创建默认工作空间）
    ↓
② 添加模型供应商（填入通义千问/OpenAI 的 API Key 和地址，点「测试」验证连通）
    ↓
③ 创建模型（在供应商下添加 Chat 模型如 qwen-plus、Embedding 模型如 text-embedding-v3）
    ↓
④ 创建知识库（上传 PDF/Word/Markdown，等待自动处理完成）
    ↓
⑤ 创建 Agent（写人设指令、选 Chat 模型、绑定知识库、设置记忆轮数）
    ↓
⑥ 对话测试（点「测试对话」，实时看到 AI 的思考过程和回复）
    ↓
⑦ 发布上线（状态改为 published，生成 API Key，外部程序即可调用）
```

### 1.5 Phase 1 交付清单

| 交付物 | 说明 |
|--------|------|
| 后端服务 | 8 个 Maven 模块、12 个 Controller、约 65 个 REST 接口 + SSE 流式协议 |
| 前端管理后台 | 认证 / 工作空间 / Agent 六维配置 / 流式对话面板 / 知识库 / 模型管理 / API Key |
| 一键部署 | `docker compose up -d --build` 四容器全栈启动 |
| E2E 脚本 | `scripts/e2e-demo.sh` 自动化验证完整闭环（14 项冒烟测试） |
| 技术文档 | 架构 / Agent 引擎 / RAG 知识库 / REST API / SSE 协议 |

**关键数据**：

| 指标 | 数值 |
|------|------|
| 后端 Java 源文件 | ~133 个 |
| 数据库表 | 20 张（Flyway V1~V13 管理） |
| REST 端点 | ~65 个 |
| 支持文档格式 | 7 种（PDF / Word / Markdown / TXT / CSV / HTML） |
| 开发用时 | 13 个日历天 |

---

## 二、技术栈总览

> 如果你对某个技术不熟悉，没关系。后文会在用到时详细解释。

| 层次 | 技术选型 | 版本 | 为什么选它 |
|------|----------|------|-----------|
| **后端框架** | Spring Boot | 3.4.0 | Java 生态最成熟的后端框架，开箱即用 |
| **Agent 引擎** | AgentScope Java 2.0（Harness + ReActAgent 内核） | 2.0.0 | 阿里巴巴开源，Agentic 自主推理路线：ReAct 推理循环 + 事件流 + Harness 生产级能力（Workspace/上下文压缩/Skill 运行时/子智能体），MVP 使用 ReActAgent 内核，Phase 2 渐进采纳 Harness 能力 |
| **RAG 框架** | Spring AI + PgVector | 1.0.0 | Spring 生态的 AI 集成方案，Workflow 路线：固定步骤的分块/向量化/检索管道，与业务同库零运维 |
| **ORM** | MyBatis-Plus | 3.5.x | 轻量 ORM，内置多租户拦截器，数据隔离业务零感知 |
| **认证** | Sa-Token（JWT 模式） | — | 比 Spring Security 轻量得多，JWT 无状态，适合多租户上下文透传 |
| **数据库** | PostgreSQL + pgvector 扩展 | 16 | 关系型数据库 + 向量检索一体化，不需要额外的向量数据库 |
| **缓存** | Redis | 7 | 会话管理、登录失败计数、API 限流 |
| **前端** | Vue 3 + Vite + Naive UI + Pinia | 3.5 / 6 / 2.40 | 现代前端方案，组件库美观且轻量 |
| **文档解析** | Apache Tika | 2.x | 支持 PDF/Word/Markdown/HTML/CSV 等几乎所有文档格式 |
| **数据库迁移** | Flyway | — | 版本化管理数据库表结构变更，团队协作不冲突 |
| **语言/运行时** | Java 17 / Node 20 | — | 后端 Spring Boot 3.4 要求 Java 17+；前端构建需要 Node 20 |

### 核心框架选型详解：AgentScope 与 Spring AI

上表列出了所有技术选型，但其中有两个是**地基级**的架构决策，值得单独展开讲：**AgentScope Java**（负责「对话」）和 **Spring AI**（负责「知识」）。理解这两个框架的分工，是理解整个系统的关键。

#### 框架一：AgentScope Java 2.0 —— 对话的大脑

**它是什么**

AgentScope 是阿里巴巴开源的多智能体框架（GitHub 4,000+ stars），最初是 Python 版本，2.0 开始提供 Java 版本。它提供了一套完整的 Agent 运行时：接收用户消息 → 推理思考 → 决定是否调用工具 → 生成回复，整个过程自动化。

AgentScope Java 2.0 的架构分为两层：

- **ReActAgent 内核**：经典的「推理 → 调工具 → 观察 → 再推理」循环，这是 1.0 就有的核心能力
- **Harness 工程化层**（2.0 新增）：在 ReActAgent 之上叠加的生产级能力套件——Workspace（工作区）、上下文压缩、长期记忆、Skill 运行时、子智能体编排、Plan 模式、Middleware 中间件、沙箱隔离等。入口类为 `HarnessAgent`

项目引入的 Maven 依赖是 `io.agentscope:agentscope-harness:2.0.0`（包含 Harness 层 + Core 内核）。**MVP 阶段有意只使用 ReActAgent 内核**，原因是：AgentOne 的多租户隔离（MyBatis 拦截器）、会话管理（DB 层 CRUD）、记忆策略（滑动窗口）已有自研实现且运行稳定，贸然切换到 Harness 的 Workspace/Session 体系会引入不必要的迁移风险。Phase 2 的 Skill 中心和 MCP 集成将评估复用 Harness 内置能力（详见 02-agent-engine.md §11）。

**我们用了它的什么**

MVP 阶段用了 AgentScope 的核心类 `ReActAgent`。它封装了完整的 ReAct（Reasoning + Acting）推理循环：

```
用户消息进入
  → ReActAgent 开始推理循环（最多 maxIters=10 轮）
      → 第 1 轮：LLM 思考「我需要做什么」→ 决定直接回答
      → （或）第 1 轮：LLM 思考「我需要查资料」→ 调用工具 → 观察结果
      → 第 2 轮：基于工具结果继续思考 → 给出最终回答
  → 输出：事件流（思考片段 + 正文片段）
```

具体用到的 API：

| API | 用在哪 | 做什么 |
|-----|--------|--------|
| `ReActAgent.builder()` | `ChatServiceImpl.buildReActAgent()` | 构建 Agent 实例：注入系统提示词、模型配置、最大迭代次数 |
| `agent.call(messages, ctx).block()` | `ChatServiceImpl.chat()` | **同步对话**：发送消息，阻塞等待完整回复 |
| `agent.streamEvents(message, ctx)` | `ChatServiceImpl.chatStream()` | **流式对话**：返回 `Flux<Event>` 事件流，逐字推送 |
| `GenerateOptions` | `buildReActAgent()` | 封装模型参数：modelName、temperature、topP、maxTokens、apiKey、baseUrl |
| `additionalBodyParam("enable_thinking", true)` | `buildReActAgent()` | 向 LLM 请求体追加自定义参数（让 Qwen3 输出思考过程） |
| `TextBlockDeltaEvent` | `chatStream()` 的事件映射 | 正文增量事件 → 转为 SSE `delta` 事件 |
| `ThinkingBlockDeltaEvent` | `chatStream()` 的事件映射 | 思考增量事件 → 转为 SSE `thinking` 事件 |

**为什么不自己写 / 不用别的**

| 方案 | 评估 |
|------|------|
| **自己写 ReAct 循环** | 需要自己实现：提示词模板、工具调用解析、多轮迭代控制、流式事件拆分、错误重试。至少 500+ 行核心代码，且边界情况（如 LLM 返回格式不规范）需要大量调试 |
| **LangChain4j** | Java 生态另一个主流选择，功能更丰富，但 API 更重，需要更多胶水代码来适配 Spring Boot。且当时 LangChain4j 的流式事件模型不如 AgentScope 直观 |
| **直接用 Spring AI 的 ChatModel** | Spring AI（含 Alibaba 扩展）走的是 **Workflow 流程编排**路线：基于 StateGraph 状态图，开发者预定义所有分支/路由/跳转，LLM 只执行节点内任务，无权改变流程走向。适合步骤固定、需审计的场景（RAG 管道、单据解析），但**不适合开放式自主推理**——Agent 需要自己决定「下一步做什么」，而不是走预设流程 |
| **AgentScope Java 2.0** ✅ | 走 **Agentic 自主智能体**路线：ReActAgent 内核提供自主推理循环 + 事件流（streamEvents），2.0 的 Harness 层进一步提供 Workspace、上下文压缩、Skill 运行时、子智能体编排等生产级能力。代码量最少，与「自主推理 + 工具调用」的需求匹配度最高 |

**最终决策理由**：两者不是平替关系，而是解决不同问题的技术底座。AgentScope 走 Agentic 路线——模型自主决策执行路径，适合开放式对话和工具调用；Spring AI 走 Workflow 路线——人掌控全链路，适合固定步骤的 RAG 管道。AgentOne 的对话引擎需要「自主推理 + 工具调用」，这正是 AgentScope 的 ReActAgent 封装好的：① 构建实例时传入系统提示词和模型配置；② 调用 `streamEvents()` 拿到事件流映射为 SSE。核心对话编排代码不到 100 行。如果自研，这部分至少 500 行，还要处理各种 LLM 返回格式不规范的边界情况。

**使用中的关键细节**：

1. **每请求新建实例**：`ReActAgent` 内部维护对话状态，不适合多线程共享。2.0 虽然引入了 Agent State 和 RuntimeContext 将状态外置，但 MVP 阶段为保持简单，仍采用每请求 `ReActAgent.builder()...build()` 新建实例的方式。

2. **`additionalBodyParam` 机制**：Qwen3 模型需要请求体里有 `enable_thinking: true` 才输出思考过程，但 AgentScope 的 `GenerateOptions` 没有这个字段。SDK 提供了 `additionalBodyParam()` 方法，内部通过 Jackson 的 `@JsonAnyGetter` 把自定义参数**平铺到 HTTP 请求体顶层**，无需修改 SDK 源码。

3. **事件过滤用 `.handle()` 不用 `.map()`**：`streamEvents()` 产出的事件类型很多（ModelCallStartEvent、BlockStartEvent 等），我们只要 TextBlockDeltaEvent 和 ThinkingBlockDeltaEvent。Reactor 的 `.map()` 要求每个输入必须产出非 null 输出，不匹配的事件无法跳过。改用 `.handle((event, sink) -> ...)`，不匹配的就不调用 `sink.next()`，自然跳过。

#### 框架二：Spring AI 1.0.0 —— 知识的管道

**它是什么**

Spring AI 是 Spring 官方推出的 AI 集成框架（2024 年发布 1.0 GA），提供了一套统一的 API 来对接各种 AI 服务：Chat（对话）、Embedding（向量化）、VectorStore（向量存储）、TextSplitter（文本分块）等。可以理解为「AI 领域的 Spring Data」—— 用 Spring 风格的接口屏蔽底层差异。

**我们用了它的什么**

与 AgentScope 不同，我们**没有用 Spring AI 的对话能力**（ChatModel），只用了它的**知识库相关能力**：

| Spring AI 组件 | 用在哪 | 做什么 |
|---------------|--------|--------|
| `TokenTextSplitter` | `ChunkService.splitByToken()` | **智能分块**：按 token 数量切分文本，在句子/段落边界断开，中英文混合文本处理正确 |
| `OpenAiEmbeddingModel` | `VectorStoreService.buildEmbeddingModel()` | **文本向量化**：把文本块转为高维向量。虽然名字叫 OpenAi，但通过配置 baseUrl 可对接任何 OpenAI 兼容 API（通义千问、DeepSeek 等） |
| `OpenAiApi` | `buildEmbeddingModel()` 内部 | 底层 HTTP 客户端，构建 `OpenAiEmbeddingModel` 时需要 |
| `embedding-model-dimensions.properties` | `ensureDimensionsResolved()` | 静态注册表，记录常见 Embedding 模型的维度（如 OpenAI text-embedding-3-small = 1536），免去 API 探测 |

**我们明确没有用的 Spring AI 组件**：

| 组件 | 为什么不用 |
|------|-----------|
| `ChatModel`（对话） | 对话由 AgentScope 的 ReActAgent 负责。Spring AI 走 Workflow 流程编排路线（StateGraph 预定义路由），适合步骤固定的任务；AgentOne 的对话引擎需要 Agentic 自主推理（模型自己决定下一步），这是 AgentScope 的专长 |
| `PgVectorStore`（向量存储） | 它只支持**一张固定维度**的表。我们需要按维度分表（`vector_store_1024`、`vector_store_1536` 等），所以自己用 JDBC 管理 |
| `VectorStore` 接口 | 同上，绑定了单表模型，不适合多知识库多维度场景 |

**为什么分块选 TokenTextSplitter 而不是自己写**

这是项目「优先使用成熟开源库」原则的典型实践：

- **TokenTextSplitter 做了什么**：按 token 数量（不是字符数量）切分文本，在句子/段落边界智能断开，处理中英文混合文本，过滤过短的块，限制最大块数
- **自己写要面对什么**：中文没有空格分词，token 计数逻辑复杂（不同模型的 tokenizer 不同）；句子边界检测要处理中英文标点；短块合并逻辑；各种边界情况
- **结论**：TokenTextSplitter 经过 Spring 社区大量测试，200 行的自研替代方案在边界覆盖上远不如它

**为什么向量化选 OpenAiEmbeddingModel 而不是其他**

Spring AI 的 `OpenAiEmbeddingModel` 虽然名字带 OpenAI，但实际是**通用的 OpenAI 兼容协议客户端**。通义千问、DeepSeek、Moonshot 等国内 LLM 厂商都提供了 OpenAI 兼容的 Embedding API，只需要改 baseUrl 和 apiKey 就能对接。一个类搞定所有厂商，不需要为每个厂商写适配器。

**使用中的关键坑**：

1. **baseUrl 自动追加 `/v1`**：Spring AI 1.0.0 的 `OpenAiApi` 会在用户配置的 baseUrl 后面自动拼接 `/v1`。如果用户填的 baseUrl 已经带 `/v1`（如 `https://dashscope.aliyuncs.com/compatible-mode/v1`），最终请求地址变成 `/v1/v1`，404。**解决**：构建前剥掉末尾的 `/v1`。

2. **ID 类型不兼容**：Spring AI 默认用 UUID 类型做向量记录 ID，但 MyBatis-Plus 生成的是 32 位 hex 字符串（无连字符），不是标准 UUID。**解决**：Flyway V6 迁移把 `vector_store` 的 id 列改为 TEXT。

3. **PgVectorStore 的维度限制**：Spring AI 的 PgVectorStore 初始化时建一张固定维度的表。换 Embedding 模型（维度不同）就报错。**解决**：放弃 PgVectorStore，自己用 JDBC 按维度建多张表（`vector_store_{dim}`）。

#### 两个框架的分工

```
┌─────────────────────────────────────────────────────────────────┐
│                        AgentOne 后端                             │
│                                                                 │
│   ┌──────────────────────┐    ┌───────────────────────────┐     │
│   │    AgentScope Java    │    │        Spring AI           │     │
│   │   ── 负责「对话」──    │    │    ── 负责「知识」──       │     │
│   │                      │    │                           │     │
│   │  • ReActAgent 推理循环│    │  • TokenTextSplitter 分块  │     │
│   │  • LLM 调用(同步/流式)│    │  • OpenAiEmbeddingModel   │     │
│   │  • 事件流(streamEvents)│   │    向量化                  │     │
│   │  • 工具调用编排       │    │  • 维度探测               │     │
│   │                      │    │                           │     │
│   │  用在:               │    │  用在:                    │     │
│   │  ChatServiceImpl     │    │  ChunkService             │     │
│   │  .buildReActAgent()  │    │  VectorStoreService       │     │
│   │  .chat()             │    │  DocumentProcessor        │     │
│   │  .chatStream()       │    │                           │     │
│   └──────────┬───────────┘    └─────────────┬─────────────┘     │
│              │                              │                   │
│              │  buildRagContext() 把检索结果  │                   │
│              │◄─────注入系统提示词────────────┘                   │
│              │                                                  │
│              ▼                                                  │
│     用户看到的对话回复                                           │
└─────────────────────────────────────────────────────────────────┘
```

**分工原则**：
- **AgentScope 管「想」**：接收用户消息 → ReAct 推理 → 调用 LLM → 产出回复。它是决策者。
- **Spring AI 管「知」**：文档分块 → 向量化 → 存储 → 检索。它是知识管道。
- **交汇点在 `buildRagContext()`**：Spring AI 检索到的文档片段，被注入到 AgentScope 的系统提示词中。AgentScope 的 ReActAgent 基于这些知识生成回答。
- **互不依赖**：两个框架没有代码级耦合。如果未来换掉其中一个（比如用 LangChain4j 替换 AgentScope），另一个完全不受影响。

> **一个常见的疑问**：Spring AI 也有 ChatModel，为什么不直接用它做对话，还要引入 AgentScope？本质上这是两种设计范式的选择：**Spring AI 走 Workflow 路线**（人预定义流程，模型只执行节点任务，控制权在开发者），**AgentScope 走 Agentic 路线**（给一个目标，模型自主决定执行路径，控制权在 LLM）。AgentOne 的对话引擎需要「自主推理 + 工具调用」——模型自己判断「要不要查知识库」「调哪个工具」，而不是走预设的固定流程。这正是 AgentScope 的 ReActAgent 封装好的。而 RAG 管道（解析 → 分块 → 向量化 → 检索）是固定步骤的流水线，不需要模型自主决策，所以用 Spring AI。两者各取所长，互不冲突。

---

## 三、系统架构

### 3.1 整体架构图

```
                         ┌──────────────────────────┐
    浏览器（Vue 3）  ───▶ │   Nginx（端口 80/8090）    │
                         │  静态资源 + 反向代理 + SSE   │
                         └───────────┬──────────────┘
                                     │ /api/*、/v1/*
                         ┌───────────▼──────────────┐
    外部程序 ──────────▶ │  Spring Boot（端口 8080）   │
   （API Key 调用）      │      8 个 Maven 模块       │
                         └──┬────────┬────────┬─────┘
                            │        │        │
                    ┌───────▼──┐ ┌───▼────┐ ┌─▼──────────────┐
                    │PostgreSQL│ │ Redis  │ │ 外部 LLM API   │
                    │+ pgvector│ │ 会话/限流│ │（OpenAI 兼容） │
                    └──────────┘ └────────┘ └────────────────┘
```

**怎么理解这张图**：
- 用户在浏览器打开网页，Nginx 提供前端页面（Vue 打包的静态文件），同时把 API 请求转发给后端。
- 后端 Spring Boot 处理所有业务逻辑，数据存在 PostgreSQL 里，临时数据（登录失败次数等）存 Redis。
- 当用户和 Agent 对话时，后端会调用外部 LLM API（如通义千问、OpenAI）生成回答。
- 外部程序可以通过 API Key 直接调用后端接口，不需要经过浏览器。

### 3.2 后端模块架构

后端采用 **Maven 多模块**结构，8 个模块分三层：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            启动层（打包部署）                                 │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │  agentone-api                                                       │   │
│   │  程序入口（AgentOneApplication）+ Flyway V1~V13 迁移 + 全局配置       │   │
│   │  依赖所有业务模块，打成可执行 jar                                      │   │
│   └────────────────────────────┬────────────────────────────────────────┘   │
│                                │ 依赖                                       │
├────────────────────────────────▼────────────────────────────────────────────┤
│                            业务层（6 个功能模块）                             │
│                                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────────────┐  │
│  │ agentone-auth │  │ agentone-    │  │        agentone-agent            │  │
│  │              │  │ workspace    │  │                                  │  │
│  │ 注册/登录    │  │              │  │  ★ 核心：对话编排                 │  │
│  │ JWT 令牌     │  │ 工作空间 CRUD│  │  ChatServiceImpl（对话入口）      │  │
│  │ 登录失败锁定 │  │ 成员角色     │  │  AgentServiceImpl（状态机）       │  │
│  │ (Redis 计数) │  │ 切换空间     │  │  MemoryService（记忆窗口）        │  │
│  └──────────────┘  └──────────────┘  │  AgentsMdParser（人设解析）       │  │
│                                      │                                  │  │
│  ┌──────────────┐  ┌──────────────┐  │  调用 ──┐    调用 ──┐           │  │
│  │ agentone-    │  │ agentone-    │  └─────────┼──────────┼───────────┘  │
│  │ apikey       │  │ skill        │            │          │              │
│  │              │  │              │            ▼          ▼              │
│  │ API Key 管理 │  │ SkillExecutor│  ┌──────────────┐ ┌──────────────┐  │
│  │ /v1/chat     │  │ 接口+注册表  │  │ agentone-    │ │ agentone-    │  │
│  │ /v1/health   │  │ HTTP Skill   │  │ knowledge    │ │ skill        │  │
│  │ 每日限额     │  │ 代码执行Skill│  │              │ │              │  │
│  │ (Redis 计数) │  │              │  │ 文档解析Tika │ │ Skill 描述   │  │
│  └──────────────┘  └──────────────┘  │ 分块ChunkSvc │ │ 注入提示词   │  │
│                                      │ 向量化存储   │ │              │  │
│                                      │ 向量检索     │ └──────────────┘  │
│                                      │ 绑定管理     │                    │
│                                      └──────────────┘                    │
│                                                                          │
│   跨模块调用关系（全部走 Service 接口，不跨层直调 Mapper）：                  │
│   • agent → knowledge：buildRagContext() 调 KnowledgeService.search()     │
│   • agent → skill：buildSkillPrompt() 调 AgentSkillService 取绑定列表      │
│   • agent → knowledge：buildReActAgent() 调 ModelMapper/ModelProviderMapper│
│   • apikey → agent：/v1/chat 复用 ChatService（与控制台同一实现）           │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│                          公共层（所有业务模块依赖）                          │
│                                                                          │
│   ┌──────────────────────────────────────────────────────────────────┐   │
│   │  agentone-common                                                 │   │
│   │                                                                  │   │
│   │  Result<T> / ResultCode      统一响应体 {code, message, data}     │   │
│   │  BusinessException           业务异常（携带错误码）                │   │
│   │  GlobalExceptionHandler      全局异常 → Result 转换               │   │
│   │  RuntimeContext (ThreadLocal) 请求上下文：userId / workspaceId     │   │
│   │  JwtAuthFilter               /api/* JWT 认证 → 写入 RuntimeContext│   │
│   │  ApiKeyAuthFilter            /v1/* API Key 认证 + 限额            │   │
│   │  WorkspaceInterceptor        MyBatis 租户拦截器（自动注入 SQL）    │   │
│   │  TokenCounter                Token 估算（中文 1.5/字 英文 0.25/符）│   │
│   └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│                          基础设施                                        │
│                                                                          │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────────┐  │
│   │ PostgreSQL16 │  │   Redis 7    │  │    外部 LLM API              │  │
│   │ + pgvector   │  │              │  │  （OpenAI 兼容协议）          │  │
│   │              │  │ 登录失败计数 │  │                              │  │
│   │ 20 张业务表  │  │ API 限额计数 │  │  对话：AgentScope 调用       │  │
│   │ + 向量维度表 │  │ 会话缓存     │  │  向量化：Spring AI 调用      │  │
│   └──────────────┘  └──────────────┘  └──────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

**依赖方向（严格单向，不允许反向依赖）**：

```
agentone-api ──依赖──▶ auth / workspace / agent / knowledge / skill / apikey
                              │
                              ▼
                       agentone-common
```

**跨模块调用的规则**：模块之间只允许通过 Service 接口调用（如 `ChatService` 调 `KnowledgeService.search()`），**不允许跨模块直接调 Mapper**（如 agent 模块直接用 knowledge 的 Mapper）。这保证了模块边界清晰，改一个模块的数据层不会影响其他模块。

### 3.3 模块目录

```
agentone-server/
├── agentone-api/          # 启动 + Flyway 迁移(V1-V13) + application.yml
├── agentone-auth/         # AuthController、AuthServiceImpl、JwtAuthFilter
├── agentone-workspace/    # WorkspaceController、WorkspaceServiceImpl
├── agentone-agent/        # ChatServiceImpl(对话核心)、AgentServiceImpl(状态机)
│                          # MemoryService、AgentsMdParser、VariableInjector
├── agentone-knowledge/    # KnowledgeServiceImpl、DocumentProcessor(异步)
│                          # DocumentParser(Tika)、ChunkService、VectorStoreService
├── agentone-skill/        # SkillExecutor 接口、SkillRegistry、HttpRequestSkill
├── agentone-apikey/       # ApiKeyServiceImpl、V1ChatController、HealthCheckController
└── agentone-common/       # Result、RuntimeContext、WorkspaceInterceptor、TokenCounter

agentone-web/src/
├── views/                 # agents / knowledge / models / api-keys / settings
├── services/              # api.ts(axios 封装) / chat.ts(SSE 解析)
├── stores/                # Pinia 状态管理（user store）
└── router/                # HTML5 history 路由
```

### 3.4 分层架构

每个模块内部遵循统一的三层结构：

```
Controller 层（参数校验 + 请求转发，不写业务逻辑）
    ↓
Service 层（业务逻辑 + 事务管理）
    ↓
Mapper 层（数据库访问，MyBatis-Plus）
    ↓
PostgreSQL / Redis / 外部 API
```

**横切组件**（所有模块共用，放在 `agentone-common`）：

| 组件 | 作用 |
|------|------|
| `JwtAuthFilter` | 拦截 `/api/*` 请求，验证 JWT 令牌，提取用户信息写入上下文 |
| `ApiKeyAuthFilter` | 拦截 `/v1/*` 请求，验证 API Key + 每日限额 |
| `WorkspaceInterceptor` | MyBatis 拦截器，自动为 SQL 追加 `workspace_id` 条件（多租户隔离的核心） |
| `GlobalExceptionHandler` | 统一异常处理，所有错误都返回 `{code, message, data}` 格式 |
| `Result<T>` | 统一响应体，`code=0` 表示成功 |

---

## 四、功能模块详解

### 4.1 用户认证与多租户隔离

#### 4.1.1 注册与登录

**功能**：用户通过邮箱+密码注册账号，注册后自动创建一个默认工作空间。

**流程**：
1. 用户提交邮箱和密码（密码要求 8~32 位）
2. 后端校验邮箱是否已注册
3. 密码使用 **BCrypt** 加密后存入数据库（BCrypt 是一种单向哈希算法，即使数据库泄露也无法反推出原始密码）
4. 自动创建默认工作空间，用户成为该空间的 owner
5. 生成 **JWT Token**（包含 userId、workspaceId、email），返回给前端
6. 后续所有请求，前端在 `Authorization: Bearer <token>` 头中携带此 Token

**登录失败保护**：
- 每次登录失败，Redis 中该邮箱的失败计数 +1
- 达到 5 次后，账号锁定 30 分钟（Redis key 自动过期）
- 这防止了暴力破解密码

#### 4.1.2 多租户隔离（重点概念）

> **什么是多租户？** 一套系统同时服务多个团队（工作空间），每个团队只能看到自己的数据，互相隔离。就像一栋公寓楼，每户有自己的钥匙，进不了别人家。

**实现方式**：MyBatis-Plus 的 `TenantLineInnerInterceptor`（租户行拦截器）。

**工作原理**：
```
用户请求 → JwtAuthFilter 从 JWT 中提取 workspaceId → 存入 ThreadLocal（RuntimeContext）
    → 业务代码正常写 SQL（不需要手动加 workspace_id 条件）
    → WorkspaceInterceptor 拦截 SQL → 自动追加 WHERE workspace_id = ?
    → 数据库只返回当前工作空间的数据
```

**举例**：业务代码写 `SELECT * FROM agent`，拦截器自动改成 `SELECT * FROM agent WHERE workspace_id = 'ws123'`。业务开发者完全不需要关心隔离逻辑。

**白名单机制**：有些表没有 `workspace_id` 列（如 `document`、`document_chunk`、`vector_store`），它们通过父实体间接隔离（例如 document 属于某个 knowledge_base，而 knowledge_base 有 workspace_id），这些表被列入白名单，拦截器不处理它们。

**一个重要的坑**：异步线程（如向量化任务）默认拿不到主线程的 ThreadLocal。必须用 `TaskDecorator` 在提交任务时捕获上下文，在工作线程中手动注入（详见第八章问题 #3）。

### 4.2 Agent 管理（六维配置 + 状态机）

#### 4.2.1 什么是 Agent

Agent（智能体）是 AgentOne 的核心概念。你可以把它理解为一个**可配置的 AI 助手**：
- 你告诉它「你是谁、怎么说话、遵守什么规则」（人设指令）
- 你告诉它「回答时先查哪些资料」（知识库绑定）
- 你告诉它「用什么模型、多高的创造力」（模型参数）
- 然后它就能按照你的要求与用户对话

#### 4.2.2 六维配置

每个 Agent 有六个维度的配置：

| 维度 | 存储方式 | 说明 |
|------|---------|------|
| **基础信息** | 文本字段 | 名称、描述、分类、图标 |
| **人格与指令** | Markdown 文本（AGENTS.md） | Agent 的人设、行为准则、回答风格。支持 `{{user_name}}`、`{{date}}` 等变量，对话时自动替换 |
| **模型策略** | JSON 字符串 | 选择哪个 Chat 模型、温度（创造性）、topP、最大 Token 数 |
| **记忆配置** | JSON 字符串 | 保留最近几轮对话（滑动窗口）、溢出策略 |
| **能力绑定** | 关联表 | 绑定哪些 Skill（技能）和知识库 |
| **高级配置** | JSON 字符串 | 预留扩展 |

**AGENTS.md 示例**：
```markdown
## 名称
客服小助手

## 核心原则
- 始终保持礼貌和专业
- 不确定的问题要如实告知用户
- 回答要简洁，不超过 200 字

## 回答风格
使用亲切的语气，适当使用 emoji
```

#### 4.2.3 状态机

Agent 有严格的状态流转规则：

```
draft（草稿）──▶ testing（测试中）──▶ published（已发布）──▶ stopped（已停用）
    ▲                │                    │                    │
    └────────────────┴──── revert（退回）──┴────────────────────┘
```

**规则**：
- 只有 `draft`、`testing`、`published` 状态的 Agent 可以对话
- 已发布（`published`）的 Agent **不能直接编辑**，必须先退回草稿（`revert`）
- 删除是**物理删除**（真删除），级联清理所有关联数据：对话消息 → 会话 → Skill 绑定 → 知识库绑定 → Agent 本体
- 整个删除过程在一个数据库事务中，要么全部删完，要么全部回滚

#### 4.2.4 更新接口的设计

Agent 更新使用 `AgentUpdateDTO`（所有字段可选），支持**部分更新**。这意味着前端四个配置 Tab（人格/模型/记忆/高级）可以独立保存，不需要每次提交全部字段。

> 这是一个踩坑后的改进：最初更新接口要求所有必填字段都传，导致前端只修改一个 Tab 时其他字段为空，报 400 错误。改为全字段可选后问题解决。

### 4.3 对话引擎（ReAct + SSE 流式）

#### 4.3.1 ReAct 循环是什么

> **ReAct**（Reasoning + Acting）是一种让 AI「边想边做」的推理框架。传统 LLM 直接给答案，ReAct 模式下的 AI 会：思考 → 决定是否使用工具 → 使用工具 → 观察结果 → 继续思考 → 给出最终答案。这个循环最多迭代 10 次。

AgentOne 使用 AgentScope Java 2.0 的 `ReActAgent` 实现 ReAct 循环。每次对话请求会新建一个 ReActAgent 实例（因为 ReActAgent 不是线程安全的，不能复用）。

#### 4.3.2 对话的完整数据流

当用户发一条消息时，后端经历以下步骤：

```
用户输入 "退款政策是什么？"
    │
    ▼
① loadAgent：加载 Agent 配置，检查状态（draft/testing/published 才可对话）
    │
    ▼
② getOrCreateSession：获取或创建会话（一个用户可以和同一个 Agent 有多个会话）
    │
    ▼
③ saveMessage(user)：把用户消息先存入数据库（这样 RAG 检索可以用当前消息作为查询）
    │
    ▼
④ buildSystemPrompt：组装系统提示词，按固定顺序拼接四段：
    ├─ 第 1 段：AGENTS.md 人设指令（变量已替换）
    ├─ 第 2 段：Skill 描述（告诉 AI 有哪些工具可用）
    ├─ 第 3 段：RAG 检索结果（从知识库检索的相关文档片段）
    └─ 第 4 段：历史对话（最近 N 轮，滑动窗口）
    │
    ▼
⑤ buildReActAgent：构建 AI 引擎实例
    ├─ 解析 chatModelId → model 表 → provider 表 → 获取 apiKey 和 baseUrl
    ├─ 设置 temperature、topP、maxTokens 等参数
    ├─ 追加 enable_thinking: true（让 Qwen3 等模型输出思考过程）
    └─ 创建 ReActAgent（maxIters=10）
    │
    ▼
⑥ agent.streamEvents()：AI 开始推理，产出事件流
    ├─ ThinkingBlockDeltaEvent → 思考过程片段
    ├─ TextBlockDeltaEvent → 正文片段
    └─ 其他事件（ModelCallStartEvent 等）静默忽略
    │
    ▼
⑦ SSE 推送给浏览器：
    event:session → 会话 ID（首个事件）
    event:thinking → 思考片段（0~N 个）
    event:delta → 正文片段（0~N 个）
    event:done → 结束标志
    │
    ▼
⑧ doOnComplete：流结束后，保存完整的 AI 回复到数据库
```

#### 4.3.3 三级模型解析链

当 Agent 配置了 `chatModelId` 时，后端需要找到对应的模型和供应商信息：

```
modelConfig.chatModelId 非空？
  ├─ 是 → 查 model 表（获取模型标识，如 "qwen-plus"）
  │       → 查 model_provider 表（获取 apiKey 和 baseUrl）
  │       → 用这些信息构建 AI 引擎
  └─ 否 → 回退到全局默认配置（环境变量 OPENAI_API_KEY / OPENAI_BASE_URL）
```

> **这是一个重要的 bug 修复**（Day 12）：最初 `chatModelId` 虽然前端保存了，但后端从未解析这个字段，导致所有请求都回退到全局默认的 `api.openai.com`（国内不可达），报 "HTTP connect timed out"。修复后补上了完整的 model → provider 两级查询。

#### 4.3.4 SSE 流式对话协议

> **SSE（Server-Sent Events）** 是一种服务器向浏览器单向推送数据的技术。与 WebSocket 不同，SSE 是单向的（只有服务器→浏览器），更轻量，适合「AI 逐字输出」的场景。

**为什么不用 EventSource？** 浏览器原生的 `EventSource` API 只支持 GET 请求，但对话需要 POST 请求体（包含消息内容）和自定义 Header（JWT Token）。所以前端使用 `fetch()` + `ReadableStream` 手动解析 SSE 流。

**事件协议**：

| 事件名 | 出现次数 | data 内容 | 说明 |
|--------|---------|-----------|------|
| `session` | 1（首个） | 会话 ID | 新建会话时前端据此记录 |
| `thinking` | 0~N | 推理片段 | AI 的思考过程（需模型支持，如 Qwen3） |
| `delta` | 0~N | 文本片段 | 回复正文增量，前端追加拼接 |
| `done` | 1 | `[DONE]` | 流正常结束 |
| `error` | 0~1 | 错误消息 | 流中途失败（替代 done） |

**完整示例**：
```
event:session
data:0195a3f2c8d17b2e9f4a6c8e0d2b4f6a

event:thinking
data:用户问的是退款政策，我需要检索知识库……

event:delta
data:根据我们的退款政策，

event:delta
data:用户可在 7 天内申请全额退款。

event:done
data:[DONE]
```

#### 4.3.5 SSE 的 ThreadLocal 上下文问题（重要工程细节）

SSE 的完成回调（`doOnComplete`）在请求线程之外触发。此时 `JwtAuthFilter` 已经清理了 ThreadLocal 中的用户信息（`RuntimeContext`）。但保存消息到数据库时，MyBatis 的租户拦截器需要 `workspaceId` 来自动填充字段。

**解决方案**：在 `chatStream` 方法入口处就捕获 `userId` 和 `workspaceId`，在 `doOnComplete` 回调中临时恢复到 `RuntimeContext`，保存完消息后再清理。

```java
// 入口捕获
final String userId = RuntimeContext.getUserId();
final String workspaceId = RuntimeContext.getWorkspaceId();

// 完成回调中恢复
.doOnComplete(() -> {
    try {
        RuntimeContext.set(userId, workspaceId, ...);
        saveMessage(assistantReply);  // 此时租户拦截器能正常工作
    } finally {
        RuntimeContext.clear();
    }
})
```

#### 4.3.6 记忆管理（滑动窗口）

AI 模型有上下文长度限制，不能把全部历史对话都塞进去。AgentOne 使用**滑动窗口**策略：

- 默认保留最近 10 轮对话（`shortTermRounds = 10`）
- 超出部分按 `overflowStrategy` 处理（默认 `sliding_window`，即丢弃最早的）
- 历史消息以文本段落的形式拼入系统提示词

### 4.4 知识库 RAG（核心重点）

> 这是 AgentOne 最核心、最复杂的模块。本章将从「什么是 RAG」讲起，逐步深入到每个技术细节。

#### 4.4.1 什么是 RAG

**RAG（Retrieval-Augmented Generation，检索增强生成）** 是一种让 LLM 基于外部知识回答的技术。

**没有 RAG 时**：
```
用户：我们公司的退款政策是什么？
AI：（不知道，但可能编造一个看似合理的回答）
```

**有 RAG 时**：
```
用户：我们公司的退款政策是什么？
系统：（先从知识库检索到《退款管理制度.docx》的相关段落）
AI：（基于检索到的真实内容回答）根据《退款管理制度》，用户可在 7 天内申请全额退款……
```

**RAG 的完整流程**：

```
                    【写入阶段：文档 → 向量】
上传文档 → 解析为纯文本 → 分块 → 每块转为向量 → 存入 PgVector

                    【读取阶段：提问 → 检索 → 注入】
用户提问 → 问题转为向量 → 在向量库中找最相似的 Top-K 块
         → 阈值过滤 → 按分数排序 → 拼入系统提示词 → LLM 基于上下文回答
```

#### 4.4.2 文档解析（Apache Tika）

**支持的文件类型**：PDF、Word（.docx/.doc）、Markdown、TXT、CSV、HTML（共 7 种，后端白名单强校验）。

**解析器选择**：Apache Tika 的 `AutoDetectParser`，它能根据文件内容（而非扩展名）自动识别格式，是 Java 生态最成熟的文档解析库。

**解析流程**：
```
MultipartFile（上传的文件）
  → Tika AutoDetectParser 解析为纯文本
  → 清理：trim + \r\n→\n + 压缩连续空行
  → 存入 document.raw_content（供失败重试）
```

**跳过嵌入资源（Day 11 的重要修复）**：

Tika 默认会递归解析文档中嵌入的所有资源（图片、缩略图、OLE 对象等）。我们发现一个问题：某些 Word 文档的 `docProps/thumbnail.wmf`（预览缩略图）中嵌入了同一篇文档的 GBK 编码文本。Tika 把缩略图里的 GBK 字节按 ISO-8859-1（Latin-1）解读，产生乱码（如 `Òª½â¾ö`，用 GBK 解码回去就是 `要解决`），混入正文。

**解决方案**：配置 Tika 跳过所有嵌入资源，只解析文档正文。

```java
private static final EmbeddedDocumentExtractor SKIP_EMBEDDED = new EmbeddedDocumentExtractor() {
    @Override
    public boolean shouldParseEmbedded(Metadata metadata) { return false; }
};
// 解析时注入到 ParseContext 中
context.set(EmbeddedDocumentExtractor.class, SKIP_EMBEDDED);
```

**为什么这样做而不是修复乱码？**
- 知识库只需要索引文档正文，缩略图里的文字本来就不该进知识库
- 从源头跳过比事后修复更干净：不会产生重复内容，也不会误伤正常的 Latin-1 文本（如法语、西班牙语）
- 对所有格式通用（不只是 docx）

**修复效果**：

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 解析字符数 | 4791（含 ~1468 乱码） | 3323 |
| 乱码字符 | 有 | 无 |
| 正文内容 | 完整 | 完整 |

#### 4.4.3 分块策略（Chunking）—— 详细解释

> **什么是分块？** 一篇几千字的文档不能直接转为向量（Embedding 模型有输入长度限制，而且长文本的语义会被「稀释」）。所以需要把文档切成小段（称为 chunk），每段独立向量化。分块的质量直接影响检索的准确度。

**分块大小的选择——为什么是 400 token？**

这里需要解释一个关键概念：**token 不等于字符**。

- **英文**：1 个单词 ≈ 1~2 个 token（如 "hello" = 1 token）
- **中文**：1 个汉字 ≈ 1~2 个 token（如 "你好" = 2 token）

400 token 大约相当于：
- 英文约 300 个单词（约 1~2 段）
- 中文约 200~400 个汉字（约 1~2 段）

这个大小是一个经验平衡点：
- **太大**（如 2000 token）：一个块里混了多个主题，检索时「不够精准」
- **太小**（如 50 token）：一个块只有一句话，缺乏上下文，检索到了也不好理解
- **400 token**：通常刚好覆盖一个完整的知识点或段落

**三种分块策略**：

| 策略 | 原理 | 适用场景 | 实现方式 |
|------|------|---------|---------|
| `by-length`（默认） | 按 token 数量切分，在句子/段落边界断开 | 大多数文档 | 委托 Spring AI 的 `TokenTextSplitter`（按 token 感知切分，不是简单的字符切分） |
| `by-title` | 按 Markdown 标题（`#`~`####`）切分，每个标题下的内容为一块 | 结构化文档（手册、FAQ、规范） | 自研，超长 section 用 TokenTextSplitter 二次切分 |
| `by-paragraph` | 按段落（`\n\n`）切分，小段落合并到接近目标大小 | 段落分明的文章 | 自研，字符级估算（字符 ≈ token × 3） |

**为什么默认策略用 TokenTextSplitter 而不是自己写？**

这遵循了「优先使用成熟开源库」的原则。Spring AI 的 `TokenTextSplitter` 经过社区大量测试，能正确处理中英文混合文本的 token 计数，在句子/段落边界智能断开。自己写 200 行的字符串切分器不仅费时，还缺乏边界情况覆盖。

**TokenTextSplitter 的关键参数**：

```java
TokenTextSplitter.builder()
    .withChunkSize(400)                    // 目标块大小（token）
    .withMinChunkSizeChars(100)            // 小于 100 字符的块会被合并到相邻块
    .withMinChunkLengthToEmbed(10)         // 小于 10 个字符的不生成向量（太短无意义）
    .withMaxNumChunks(10000)               // 单文档最大块数（防止异常大文档）
    .withKeepSeparator(true)               // 保留分隔符
    .build();
```

**Overlap（重叠）机制**：

分块时，相邻块之间会有 60 个字符的重叠（默认值）。也就是说，下一块的开头会包含上一块结尾的 60 个字符。

**为什么需要 overlap？** 假设一个关键信息刚好在两个块的边界上：

```
没有 overlap：
  块 1: "...退款金额为实际支付"
  块 2: "金额的 100%，手续费..."
  → 检索到块 1 时不知道金额是多少，检索到块 2 时不知道是什么金额

有 overlap（60 字符）：
  块 1: "...退款金额为实际支付"
  块 2: "金额为实际支付金额的 100%，手续费..."
  → 块 2 包含了完整信息
```

**标题上下文注入（injectHeadingContext）**：

对于 `by-length` 和 `by-paragraph` 策略，分块后每个块可能不知道自己属于文档的哪个章节。标题上下文注入解决了这个问题：

```
原始块: "退款金额为实际支付金额的 100%..."
注入后: "[退款政策] 退款金额为实际支付金额的 100%..."
```

这样即使块被单独检索出来，LLM 也知道它属于「退款政策」章节。

> **注意**：这个机制只对 Markdown 格式的标题有效（以 `#` 开头的行）。PDF 文档解析后通常没有 Markdown 标题，所以这个增强在 PDF 上基本不生效。

#### 4.4.4 向量化与存储（Embedding + PgVector）

> **什么是 Embedding（嵌入/向量化）？** 把一段文本转换成一个高维数字向量（如 1536 维的浮点数数组）。语义相近的文本，向量在空间中的距离也近。这样就能通过计算向量距离来衡量文本的「语义相似度」。

**举例**：
```
"退款政策" → [0.12, -0.34, 0.56, ..., 0.78]  （1536 个数字）
"退货规定" → [0.11, -0.33, 0.55, ..., 0.77]  （很接近！语义相似）
"天气预报" → [-0.45, 0.67, -0.12, ..., 0.23]  （差很远，语义不同）
```

**Embedding 模型的选择**：

AgentOne 不绑定特定的 Embedding 模型。每个知识库可以选择不同的 Embedding 模型（如通义千问的 `text-embedding-v3`、OpenAI 的 `text-embedding-3-small`），系统会根据 Provider 配置动态构建 Spring AI 的 `OpenAiEmbeddingModel`。

**维度隔离表设计（重要设计决策）**：

不同 Embedding 模型产生不同维度的向量：
- OpenAI `text-embedding-3-small` → 1536 维
- 通义千问 `text-embedding-v3` → 1024 维
- 某些小模型 → 768 维

一张表无法存储不同维度的向量。所以 AgentOne 采用**按维度分表**：

```
vector_store_1536  → 存 1536 维的向量
vector_store_1024  → 存 1024 维的向量
vector_store_768   → 存 768 维的向量
```

每个知识库根据自己的 Embedding 模型维度，自动落到对应的表。这些表在首次使用时**懒创建**（不需要预先建好），并自动创建 IVFFlat 余弦索引。

**为什么不用 Spring AI 自带的 PgVectorStore？** 因为 PgVectorStore 只支持一张固定维度的表。我们需要支持多个知识库使用不同维度的 Embedding 模型，所以自己用 JDBC 管理多张维度表。

**一个必须注意的坑——ID 类型**：

Spring AI 默认使用 UUID 类型作为向量记录的 ID。但 MyBatis-Plus 生成的 ID 是 32 位十六进制字符串（如 `0195a3f2c8d17b2e9f4a6c8e0d2b4f6a`），不是标准 UUID 格式（没有连字符）。如果 `vector_store` 的 `id` 列类型是 UUID，插入会失败。

**解决方案**（Flyway V6 迁移）：将 `vector_store` 的 `id` 列类型改为 `TEXT`。

#### 4.4.5 向量检索（余弦相似度）

当用户提问时，系统用**余弦相似度**在向量库中找最相关的文档块：

```sql
SELECT id, 1 - (embedding <=> ?::vector) AS score
FROM vector_store_1024
WHERE metadata->>'knowledgeId' = '目标知识库ID'
ORDER BY embedding <=> ?::vector
LIMIT 5  -- Top-K
```

**解释**：
- `<=>` 是 pgvector 扩展提供的**余弦距离**算子（值域 0~2，越小越相似）
- `score = 1 - 距离`（值域 0~1，越大越相似）
- `WHERE metadata->>'knowledgeId'` 保证只检索当前知识库的向量，防止跨库串数据
- `LIMIT 5` 取最相似的 5 个块（Top-K，K 可配置）

**余弦相似度 vs 欧几里得距离**：
- 余弦相似度衡量的是两个向量「方向」的一致性，不受向量长度影响
- 对于文本语义比较，余弦相似度比欧几里得距离更合适，因为「方向」比「长度」更能反映语义

#### 4.4.6 RAG 注入对话（过滤 + 排序 + Token 预算）

检索到 Top-K 个文档块后，不能全部塞给 LLM（会超出上下文窗口，也会引入噪声）。AgentOne 的注入策略：

```
① 逐知识库检索 Top-K（每个知识库独立检索）
    ↓
② 按各绑定的 similarityThreshold 过滤（默认 0.7，低于此分数的丢弃）
    ↓
③ 全局按 score 降序排序（跨知识库统一排名）
    ↓
④ 在 3000 token 预算内截取（从最高分开始累加，超预算停止）
    ↓
⑤ 拼成「## 参考知识」块，注入系统提示词
```

**相似度阈值（similarityThreshold）的选择——为什么是 0.7？**

这是一个经验值，但**并不完美**。实际测试发现：
- 很多真实查询的余弦相似度分数只有 0.5~0.6
- 0.7 的阈值会把这些「其实相关但分数不高」的结果也过滤掉
- 导致 Agent 回答「查无此内容」

**当前的处理方式**：阈值已改为前端可调（每个知识库绑定独立配置，范围 0~1）。但默认值 0.7 的**科学校准**需要评测数据集支撑，列为后续优化项。

**Token 预算（RAG_MAX_TOKENS = 3000）**：

3000 token 约等于 1500~3000 个汉字。这个预算要平衡两个因素：
- **太小**：只能放入 1~2 个文档块，信息不够
- **太大**：占据过多上下文窗口，挤压 AI 的「思考空间」，也增加 API 调用成本

Token 估算使用 `TokenCounter` 工具类：中文按 1.5 token/字、英文按 0.25 token/字符估算。

**优雅降级**：整个 RAG 注入过程被 try/catch 包裹。如果知识库不可用（如 Embedding API 挂了），返回空字符串，**绝不影响正常对话**。Agent 会像没有知识库一样正常回答。

#### 4.4.7 异步处理与失败恢复

文档上传后的处理分为**同步**和**异步**两段：

**为什么拆两段？**
- **同步段必须在请求线程**：`MultipartFile`（上传的文件）在 HTTP 请求结束后会被清理。所以解析必须在请求还在时完成，并把文本存入数据库（`raw_content`）。
- **异步段是耗时操作**：向量化需要分批调用外部 Embedding API（每批 20 条），大文档可能需要几十秒甚至几分钟，不能让前端一直等着。

**失败恢复机制**：
- 向量化失败时，文档状态变为 `error`，记录错误信息（`errorMsg`）
- 因为原文已存入 `raw_content`，用户可以点击「重试」，系统从 `raw_content` 重新分块和向量化，**不需要重新上传文件**
- 但如果是解析阶段就失败了（如 Tika 无法识别的格式），`raw_content` 为空，只能删除后重新上传

**异步线程池配置**：
```
核心线程数: 2
最大线程数: 4
队列容量: 100
拒绝策略: CallerRunsPolicy（队列满时由请求线程兜底执行，不丢任务）
```

**@Async 的一个坑**：Spring 的 `@Async` 注解靠 AOP 代理生效。如果在同一个类中调用 `@Async` 方法（自调用），代理不会生效，方法会同步执行。所以异步处理方法被拆到独立的 `DocumentProcessor` 类中，由 `KnowledgeServiceImpl` 跨 Bean 调用。

#### 4.4.8 知识库检索质量评估（诚实的现状说明）

**当前水平**：MVP 级别。工程骨架（异步/租户/事务/重试/失败可恢复）扎实，但检索质量栈仍是基础水平。

**已知的检索质量问题**（按严重程度排序）：

| 问题 | 影响 | 状态 |
|------|------|------|
| 纯向量检索，无关键词/混合检索 | 精确数字、日期、条款号查不准 | 待优化 |
| 无重排（rerank） | 通用套话可能排在真正相关内容前面 | 待优化 |
| 无查询改写 | 多轮对话中「它的」「那个」等指代无法正确检索 | 待优化 |
| 阈值 0.7 未校准 | 真实查询分数常低于 0.7，被全部过滤 | 已改为可调，校准待评测数据 |
| 无评测体系 | 准确率无法量化，改动效果无法验证 | 待建设 |
| PDF 表格被拍平 | 财报/报表中的表格结构丢失 | 待优化 |

**适用场景**：「人在环、答错可接受」的内部知识助手（如内部 FAQ、产品手册查询）。

**不适用场景**：「准确率可断言、表格/数字密集」的企业级场景（如财报分析、合同审查），需要后续迭代补齐混合检索、重排、评测体系。

详细的 68 项改进清单见 `docs/technical/improvements.md`。

### 4.5 模型管理（供应商 + 模型两层结构）

#### 4.5.1 两层结构

```
model_provider（供应商）
  ├─ 名称：如 "通义千问"
  ├─ apiKey：API 密钥（如 sk-xxxx）
  ├─ baseUrl：API 地址（如 https://dashscope.aliyuncs.com/compatible-mode）
  └─ model（模型，一个供应商下可挂多个模型）
       ├─ Chat 模型：如 qwen-plus（用于对话）
       ├─ Embedding 模型：如 text-embedding-v3（用于向量化）
       ├─ Rerank 模型：预留
       └─ Image2Text 模型：预留
```

**为什么分两层？** 一个供应商（如通义千问）通常提供多个模型（qwen-plus、qwen-max、text-embedding-v3 等），它们共用同一个 apiKey 和 baseUrl。两层结构避免了重复配置。

#### 4.5.2 连通性检测

配置完供应商后，可以点击「测试连通性」，后端会发一个轻量请求（"Hi"）到 LLM API，验证 apiKey 和 baseUrl 是否正确。返回 `{available: true/false, message: "...", latencyMs: 123}`。

#### 4.5.3 Embedding 维度自动探测

用户添加 Embedding 模型时不需要填向量维度。首次使用时，系统自动探测：
1. 先查 Spring AI 的静态注册表（`embedding-model-dimensions.properties`，覆盖 OpenAI/Cohere 等常见模型）
2. 查不到就调一次 API，用返回的向量数组长度作为维度
3. 回写到 `model.dimensions` 字段，之后直接读取

#### 4.5.4 baseUrl 的 /v1 坑

Spring AI 1.0.0 的 `OpenAiApi` 会自动在 baseUrl 后面拼接 `/v1` 前缀。如果用户配置的 baseUrl 已经带了 `/v1`（如 `https://dashscope.aliyuncs.com/compatible-mode/v1`），会变成 `/v1/v1`，导致 404 错误。

**解决方案**：构建 Embedding 模型前，先检查并剥掉 baseUrl 末尾的 `/v1`。

### 4.6 Skill 技能系统

#### 4.6.1 当前状态（MVP）

Skill（技能）是 Agent 可以使用的「工具」。Phase 1 实现了 Skill 框架和 3 个内置 Skill：

| Skill | 功能 |
|-------|------|
| 知识库检索 | 从绑定的知识库中检索相关信息（实际由 RAG 注入实现，不走 Skill 通道） |
| HTTP 请求 | 调用外部 API（基于 WebClient） |
| 代码执行 | 执行 JavaScript 代码（简化版，生产环境需要 Docker 沙箱） |

**MVP 的限制**：当前 Skill 只是以文本描述的形式注入系统提示词（告诉 LLM「你有这些工具可用」），**并未注册为 ReActAgent 的真正工具**。也就是说，LLM 知道有这些工具，但还不能真正调用它们执行操作。真正的 function calling（工具调用）是 Phase 2 的内容。

#### 4.6.2 Skill 框架设计

```
SkillExecutor（接口）
  ├─ execute(invocation, context) → SkillResult  // 执行技能
  ├─ getDescriptor() → SkillDescriptor           // 名称/描述/参数 schema
  └─ isAvailable()                               // 是否可用

SkillRegistry：执行器注册表（启动时自动注册所有 SkillExecutor Bean）
agent_skill_binding：Agent 与 Skill 的绑定关系（可启用/停用）
```

扩展新 Skill 只需实现 `SkillExecutor` 接口，框架会自动注册。

### 4.7 API Key 与开放接口

#### 4.7.1 API Key 管理

- **创建**：使用 `SecureRandom` 生成 32 字节随机数 → Base64URL 编码 → 拼接前缀 `abx_sk_live_`
- **存储**：数据库只存 **SHA-256 哈希值**，明文只在创建时返回一次（之后无法再看到）
- **权限**：每个 Key 可限制允许调用的 Agent 列表（`allowedAgents`）
- **限流**：Redis 按天计数（key 格式 `apikey:usage:{id}:{yyyyMMdd}`），超过 `dailyLimit`（默认 1000 次/天）拒绝

#### 4.7.2 开放接口

| 接口 | 认证方式 | 说明 |
|------|---------|------|
| `POST /v1/chat` | X-API-Key | 同步对话 |
| `POST /v1/chat/stream` | X-API-Key | 流式对话（SSE） |
| `GET /v1/health` | 无需认证 | 健康检查（数据库 + Redis 连通性） |

开放接口复用与控制台完全相同的 `ChatService` 实现，保证行为一致。

---

## 五、前端界面

### 5.1 技术选型

| 技术 | 用途 |
|------|------|
| Vue 3（Composition API） | 前端框架 |
| Vite 6 | 构建工具 |
| Naive UI 2.40 | 组件库（按钮、表格、弹窗等） |
| Pinia | 状态管理（用户信息、Token 等） |
| Vue Router（HTML5 history） | 路由管理 |
| marked + DOMPurify | Markdown 渲染 + XSS 防护 |

### 5.2 主要页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 登录/注册 | `/login` | 邮箱注册、登录、JWT 认证 |
| Agent 列表 | `/agents` | 卡片式展示所有 Agent，状态呼吸点，悬浮编辑/删除 |
| Agent 详情 | `/agents/:id` | 四个配置 Tab（人格/模型/记忆/高级）+ 测试对话 + 发布/停用 |
| 对话面板 | 滑出式抽屉 | SSE 流式对话、思考过程展示、Markdown 渲染、会话管理 |
| 知识库 | `/knowledge` | 知识库 CRUD、文档拖拽上传、处理状态轮询、检索测试 |
| 模型管理 | `/models` | 供应商 CRUD、模型管理、连通性测试 |
| API Key | `/api-keys` | 创建/列表/停用 API Key |

### 5.3 对话面板的实现细节

- **双侧布局**：用户消息靠右（渐变背景气泡），AI 消息靠左（白色卡片）
- **思考过程**：可折叠面板，实时显示 AI 的 thinking 事件，正文输出开始后自动折叠
- **Markdown 渲染**：AI 回复通过 `marked.parse()` 渲染为 HTML，再经 `DOMPurify.sanitize()` 过滤 XSS
- **流式中断**：AbortController 可随时中止流式请求
- **SSE 解析**：`fetch()` + `ReadableStream` 手动解析（因 EventSource 不支持 POST）

---

## 六、数据库设计

### 6.1 表结构概览（20 张表）

| 域 | 表名 | 说明 |
|----|------|------|
| **账户与租户** | `sys_user` | 用户（邮箱、BCrypt 密码） |
| | `workspace` | 工作空间 |
| | `user_workspace` | 用户-工作空间关联（角色：owner/member） |
| **Agent** | `agent` | Agent 配置（六维配置存 JSONB/文本） |
| | `chat_session` | 对话会话 |
| | `chat_message` | 对话消息（role=user/assistant） |
| **知识库** | `knowledge_base` | 知识库（含分块配置） |
| | `document` | 文档（含 raw_content 原文、状态、错误信息） |
| | `document_chunk` | 文档分块（内容、token 数） |
| | `vector_store_{dim}` | 向量存储（按维度隔离，运行时懒建） |
| **模型** | `model_provider` | 模型供应商（apiKey、baseUrl） |
| | `model` | 模型（chat/embedding/rerank，含维度） |
| **Skill** | `skill` | 技能定义 |
| | `agent_skill_binding` | Agent-Skill 绑定 |
| | `agent_knowledge_binding` | Agent-知识库绑定（topK、阈值） |
| **开放 API** | `api_key` | API Key（SHA-256 哈希存储） |
| | `chat_api_log` | API 调用日志 |
| **Phase 2 预留** | `mcp_server`、`scheduled_task` 等 | 暂未使用 |

### 6.2 主键策略

业务表主键使用 MyBatis-Plus 的 `ASSIGN_UUID`（32 位十六进制字符串，无连字符），如 `0195a3f2c8d17b2e9f4a6c8e0d2b4f6a`。

### 6.3 数据库迁移

使用 Flyway 管理，从 V1 到 V13 共 13 个迁移版本。每次修改表结构都通过新建迁移文件实现，保证团队成员和部署环境的数据库结构一致。

| 关键迁移 | 内容 |
|---------|------|
| V1 | 基础表（用户、工作空间、Agent） |
| V3 | 知识库相关表 |
| V6 | **修复** vector_store id 类型为 TEXT（兼容 32 位 hex） |
| V8 | 模型两层重构（Provider → Model） |
| V11 | 知识库增加分块配置（strategy/size/overlap） |
| V12 | document 增加 raw_content 列（支持失败重试） |
| V13 | 预留表（Phase 2） |

---

## 七、部署方案（Docker 一键部署）

### 7.1 部署架构

```
docker compose up -d --build
    │
    ├── postgres（PostgreSQL 16 + pgvector 扩展）
    ├── redis（Redis 7）
    ├── backend（Spring Boot，多阶段 Dockerfile 构建）
    └── frontend（Vue 3 构建 → Nginx 托管）
```

### 7.2 零代码容器化

后端配置注入是一个巧妙的设计：`application.yml` 中硬编码了本地开发的数据库/Redis 地址，但 Docker 部署时通过 Spring Boot 标准环境变量覆盖：

```yaml
# docker-compose.yml
backend:
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/agentone?stringtype=unspecified
    SPRING_DATA_REDIS_HOST: redis
```

Spring Boot 会自动用环境变量覆盖 yml 中的同名配置，**一行代码都不用改**。本地开发时环境变量不存在，继续用 yml 中的默认值。

> `stringtype=unspecified` 必须保留，这是 JSONB 字段绑定的依赖。

### 7.3 Nginx SSE 配置

Nginx 默认会缓冲上游响应（攒够一批再转发），且默认 `proxy_read_timeout 60s`。这会导致：
- 流式对话不能逐字输出（被缓冲了）
- 超过 60 秒的长对话被掐断

**解决方案**（`nginx.conf`）：
```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;           # 来一条转一条，不缓冲
    proxy_read_timeout 3600s;      # 1 小时超时，长对话不被掐断
}
```

### 7.4 健康检查

后端启动后，Docker 通过 `curl -f http://localhost:8080/v1/health` 检测健康状态。`start_period` 设为 180 秒，因为首次启动需要执行 Flyway 13 个迁移 + AgentScope 初始化，低配机器可能需要几分钟。

---

## 八、遇到的问题与解决方案（踩坑全记录）

> 以下按时间顺序记录了 Phase 1 开发中遇到的所有重要问题，包括现象、排查过程、根因和解决方案。这些经验对后续开发和维护极有价值。

### 问题 1：循环依赖（Day 1）

**现象**：auth 模块需要调用 workspace 模块创建默认工作空间，但 workspace 模块也依赖 auth 模块，形成循环依赖，编译失败。

**解决方案**：将 `WorkspaceService` 接口下沉到 `agentone-common` 模块，`workspace` 模块提供实现。auth 模块依赖 common 中的接口，不直接依赖 workspace 模块。

**教训**：多模块项目中，公共接口应放在底层模块，实现放在上层模块。

### 问题 2：AgentScope Java 2.0 的存在性存疑（Day 2）

**现象**：最初不确定 AgentScope 是否有 Java 版本（它最初是 Python 框架）。

**排查**：在 GitHub 上调研确认 `agentscope-ai/agentscope-java`（4,297 stars，阿里巴巴团队），Maven 坐标 `io.agentscope:agentscope:2.0.0-SNAPSHOT`。

**决策**：采用 AgentScope Java 2.0 替代自写的 LLM 调用层。它内置了 ReAct 循环、事件流、会话管理，省去大量自研代码。

### 问题 3：异步线程丢失租户上下文（Day 4）

**现象**：文档上传后触发异步向量化，异步线程访问数据库报 `workspace_id` 为空错误。

**根因**：多租户的 `workspaceId` 存在 ThreadLocal 中，异步线程是新的线程，默认拿不到主线程的 ThreadLocal。

**解决方案**：在线程池配置中使用 `TaskDecorator`，在提交任务时（请求线程）捕获 `RuntimeContext`，在执行任务时（工作线程）注入，执行完再清理。

```java
executor.setTaskDecorator(runnable -> {
    Context context = RuntimeContext.get();  // 提交线程捕获
    return () -> {
        try {
            RuntimeContext.set(context);     // 工作线程注入
            runnable.run();
        } finally {
            RuntimeContext.clear();          // 执行完清理
        }
    };
});
```

**教训**：凡是使用 ThreadLocal 传递上下文的项目，异步线程必须手动传播。

### 问题 4：不同 Embedding 模型维度冲突（Day 4）

**现象**：使用 Spring AI 的 `PgVectorStore` 时，切换 Embedding 模型后报错——旧表是 1536 维，新模型产生 1024 维向量，维度不匹配。

**解决方案**：放弃 PgVectorStore 的单表方案，改为**按维度分表**（`vector_store_{dim}`），用 JDBC 自己管理。不同维度的向量存不同的表，互不干扰。

### 问题 5：PDF 上传 500 错误——四个 bug 连环（Day 10）

上传 PDF 时连续暴露了四个问题：

| # | 现象 | 根因 | 修复 |
|---|------|------|------|
| 1 | 上传大文件报 500 | 未配置 multipart 大小限制（默认 1MB） | `application.yml` 配置 50MB |
| 2 | `NoClassDefFoundError: ChecksumInputStream` | commons-io 版本过低 | 升级到 2.18.0 |
| 3 | `OutOfMemoryError` | Tika 全量依赖包含 50+ 格式解析器，内存爆炸 | 替换为 pdf/microsoft/html/text 单独模块 |
| 4 | `StringIndexOutOfBoundsException` | ChunkService 未处理短文本（空字符串切分越界） | 增加 early return + 边界检查 |

**教训**：文件上传是一个看似简单但坑很多的功能，文件大小、依赖版本、内存、边界条件都需要覆盖。

### 问题 6：错误状态被事务回滚（Day 10）

**现象**：文档向量化失败后，`status=error` 和 `errorMsg` 没有保存到数据库——因为 `@Transactional` 在异常时把整个事务回滚了，包括状态更新。

**解决方案**：异步处理方法（`processAsync`）不标注 `@Transactional`。失败时单独调用 `markError()` 保存错误状态（这个方法有独立事务）。

### 问题 7：docx 检索结果乱码（Day 11）

**现象**：知识库检索结果中，部分分块出现 `Òª½â¾ö²âÊÔ...` 乱码。前 10 个块正常，后几个块乱码。

**排查过程（分层排除法）**：
1. 查数据库：`document_chunk.content` 里**存的就是乱码**（排除显示层问题）
2. 解码分析：`Òª½â¾ö` 用 GBK 解码 = `要解决` → 判定是「GBK 字节被当 Latin-1 读」
3. 后端是 PostgreSQL（JDBC 恒为 UTF-8），Java 全程用 String → 排除数据库/传输/前端
4. 直接 UTF-8 解析 `word/document.xml` → 全文 3126 字**完全正常** → 排除「文件本身坏了」
5. 但数据库里 `raw_content` 有 4791 字，多出来的 ~1468 字全是乱码 → 说明乱码来自正文之外
6. 检查 docx 压缩包：`docProps/thumbnail.wmf`（74KB 预览图）里嵌着同一篇文档的 **GBK 文本** → **真凶找到了**

**根因**：Tika 的 `AutoDetectParser` 默认递归解析嵌入资源，把缩略图里的 GBK 文本按 ISO-8859-1 误读成乱码，拼到正文后面。

**解决方案**：配置 Tika 跳过所有嵌入资源（详见 4.4.2 节）。

**教训**：乱码问题的排查关键是**确定乱码第一次出现在哪一层**。从显示层 → 传输层 → 存储层 → 解析层逐层排除。

### 问题 8：chatModelId 从未被解析（Day 12）

**现象**：所有对话都报 "HTTP connect timed out"，即使已经配置了通义千问的 API Key。

**根因**：前端「模型策略」Tab 选择的 Chat 模型保存为 `chatModelId`，但后端 `buildReActAgent()` 从未读取这个字段。所有请求都回退到全局默认的 `api.openai.com`（国内不可达）。

**解决方案**：补上 `chatModelId → model 表 → provider 表` 的两级解析链（详见 4.3.3 节）。

**教训**：「前端存了、后端没用」的数据通路断裂 bug，只有全链路端到端测试才能发现。

### 问题 9：Reactor .map() 返回 null（Day 12）

**现象**：流式对话报 "mapper returned a null value" 异常。

**根因**：AgentScope 的 `streamEvents()` 产出多种事件类型（TextBlockDeltaEvent、ThinkingBlockDeltaEvent、ModelCallStartEvent 等），我们只想处理前两种。使用 `.map()` 时，不匹配的事件返回 null，但 Reactor 的 `.map()` **禁止返回 null**。

**解决方案**：改用 `.handle((event, sink) -> ...)`，不匹配的事件不调用 `sink.next()`（跳过），匹配的才发射。

```java
agent.streamEvents(userMsg, ctx)
    .<ServerSentEvent<String>>handle((event, sink) -> {
        if (event instanceof TextBlockDeltaEvent textEvent) {
            sink.next(deltaSSE);           // 处理
        } else if (event instanceof ThinkingBlockDeltaEvent thinkingEvent) {
            sink.next(thinkingSSE);        // 处理
        }
        // 其他事件：不调用 sink，自动跳过
    });
```

### 问题 10：Qwen3 模型不输出思考过程（Day 12）

**现象**：前端思考过程面板始终为空，没有 thinking 事件。

**根因**：Qwen3 模型默认不输出 `reasoning_content`，需要在请求体中传 `enable_thinking: true`。

**解决方案**：通过 AgentScope SDK 的 `additionalBodyParam("enable_thinking", true)` 传递。SDK 内部通过 `@JsonAnyGetter` 将这个参数平铺到 HTTP 请求体顶层。

### 问题 11：Agent 状态序列化不一致（Day 12）

**现象**：前端状态映射用小写 `published` 作 key，但 API 返回大写 `PUBLISHED`，匹配失败，显示原始大写字符串。

**根因**：`AgentVO.status` 是枚举类型，Jackson 默认按枚举名序列化（`PUBLISHED`）。

**解决方案**：在 `AgentStatus.getCode()` 方法上加 `@JsonValue` 注解，序列化为小写 code（`published`/`draft`/`testing`/`stopped`）。

### 问题 12：删除是软删除但文案说「不可恢复」（Day 12）

**现象**：用户点击「删除」后，确认弹窗写着「此操作不可恢复」，但实际上删除只是把状态改为 `ARCHIVED`（软删除），而且列表没有过滤归档状态，Agent 还挂在列表上。

**决策**：与用户确认后改为**物理删除**（真删除），级联清理所有关联数据。与「不可恢复」的文案保持一致。

### 问题 13：健康检查断言失败（Day 13）

**现象**：E2E 脚本断言 `/v1/health` 返回的 `.status` 字段，但实际返回的是 `.data.status`。

**根因**：`/v1/health` 走了统一的 `Result` 包装（`{code: 0, data: {status: "UP"}}`），状态在 `data` 里面。

**解决方案**：修改断言为 `(.data.status // .status)`，同步修正 API 文档和 README。

### 问题 14：Docker 镜像拉取卡死（Day 13）

**现象**：本机 `docker compose up --build` 时，拉取 docker.io 的大镜像层卡死不动。

**解决方案**：
- 基础镜像走 daocloud 镜像加速
- 后端 Dockerfile 配置阿里云 Maven 镜像（`maven-settings.xml`）
- 后端多阶段构建受本机网络限制未完成，改用「本地 mvn 打 jar + 运行时镜像」等价验证

### 问题 15：文档计数漂移（Day 13 之前）

**现象**：知识库列表显示的文档数/分块数不准确。

**根因**：计数靠应用层 `+1/-1` 维护，并发操作或异常路径（如向量化失败回滚）会导致计数漂移。

**解决方案**：改为每次操作后从数据库 `COUNT(*)` 实时重算（`recomputeKbCounts`），覆盖上传/重试/删除/异步向量化四条路径。

---

## 九、关键技术决策与权衡

| 决策点 | 选择 | 理由 | 备选方案 | 权衡 |
|--------|------|------|---------|------|
| Agent 引擎 | AgentScope Java 2.0 | 内置 ReAct 循环、事件流、会话管理，省去大量自研代码 | LangChain4j（需更多胶水代码） | 学习成本 vs 开发效率 |
| 向量存储 | PgVector（与业务同库） | 零额外运维，不需要部署独立的向量数据库 | Milvus / Weaviate（多一套组件） | 规模上限 vs 运维简单 |
| 认证框架 | Sa-Token JWT | 轻量、无状态、配置简单 | Spring Security（功能丰富但配置繁重） | 功能丰富度 vs 简单性 |
| 多租户实现 | MyBatis 拦截器自动注入 | 业务代码零感知，不容易遗漏 | 手动在每条 SQL 加 WHERE（易遗漏） | 灵活性 vs 安全默认 |
| SSE 客户端 | fetch + ReadableStream | 需要 POST body 和自定义 Header | EventSource（仅支持 GET） | 兼容性 vs 能力 |
| Skill 接入方式（MVP） | 提示词注入（文本描述） | 快速闭环，不需要修改 AgentScope 的工具注册 | ReAct 工具注册（function calling） | 真调用能力推迟到 Phase 2 |
| 删除策略 | 物理删除 + 级联 | 与「不可恢复」的用户提示一致 | 软删除（可恢复但增加复杂度） | 可恢复性 vs 一致性 |
| 配置注入 | 环境变量覆盖 | 零代码容器化，本地默认值原样保留 | 多 profile yml（维护两份配置） | 两份维护 vs 零改动 |
| 文档解析器 | Tika + 跳过嵌入资源 | 全格式通用，从源头避免乱码 | POI 专用解析（仅 docx，不通用） | 通用性 vs 专用优化 |
| 分块实现 | Spring AI TokenTextSplitter | 框架提供、token 感知、社区测试充分 | 纯自研字符切分 | 遵循「优先开源库」原则 |

---

## 十、当前局限与 Phase 2 展望

### 10.1 已知局限

| 方面 | 现状 | 影响 |
|------|------|------|
| Skill 系统 | 仅提示词注入，未注册为 ReAct 工具 | Agent 不能真正调用工具执行操作 |
| 检索质量 | 纯向量、无混合检索、无重排、无评测 | 精确数字/条款号查不准，准确率无法量化 |
| 列表分页 | 所有列表接口无分页 | 数据量大后性能差 |
| RBAC | 仅 owner/member 两级 | 无法细粒度控制权限 |
| 测试 | 知识库模块零单元测试 | 分块逻辑无回归保障 |
| 配置 | application.yml 硬编码 DB/Redis 地址 | 靠环境变量绕过，不够优雅 |

### 10.2 Phase 2 计划

| 功能 | 说明 |
|------|------|
| Skill 中心 | 把 Skill 注册为 ReActAgent 工具，实现真正的 function calling |
| Skill 调试器 | 三步向导、上下文注入、单步执行 |
| MCP 集成 | 连接外部 MCP Server，自动发现和注册工具 |
| IM Bot 网关 | 对接企业微信/钉钉，@触发 Agent 对话 |
| 监控仪表盘 | 对话日志、Skill 调用链、检索质量指标 |
| RBAC 权限 | 4 种角色、细粒度权限控制 |

---

## 十一、概念词汇表

| 术语 | 解释 |
|------|------|
| **Agent（智能体）** | 可配置的 AI 助手。你给它设定人设、知识库、模型参数，它就能按要求与用户对话 |
| **RAG（检索增强生成）** | 先从知识库检索相关信息，再让 LLM 基于检索结果回答。减少幻觉，让 AI 能回答私有数据问题 |
| **Embedding（向量化/嵌入）** | 把文本转换为高维数字向量。语义相近的文本，向量距离也近。是 RAG 的基础 |
| **Token** | LLM 处理文本的最小单位。英文 1 词 ≈ 1~2 token，中文 1 字 ≈ 1~2 token。模型的输入/输出长度以 token 计 |
| **Chunk（分块）** | 把长文档切成的小段。每段独立向量化和检索。分块质量直接影响检索准确度 |
| **Overlap（重叠）** | 相邻分块之间重叠的字符数。防止关键信息在块边界被截断 |
| **余弦相似度** | 衡量两个向量方向一致性的指标（0~1，越大越相似）。用于文本语义比较 |
| **PgVector** | PostgreSQL 的向量检索扩展。让关系型数据库具备向量存储和相似度搜索能力 |
| **ReAct** | Reasoning + Acting。一种 AI 推理框架：思考 → 使用工具 → 观察结果 → 继续思考 → 给出答案 |
| **SSE（Server-Sent Events）** | 服务器向浏览器单向推送数据的技术。用于 AI 逐字输出回复 |
| **JWT（JSON Web Token）** | 一种无状态的认证令牌。包含用户信息，服务器不需要存 session |
| **多租户** | 一套系统服务多个团队，每个团队数据互相隔离 |
| **BCrypt** | 一种密码哈希算法。单向加密，即使数据库泄露也无法反推原始密码 |
| **Flyway** | 数据库版本迁移工具。用 SQL 文件管理表结构变更，像 Git 管理代码一样管理数据库 |
| **ThreadLocal** | Java 中线程级别的变量存储。每个线程有自己的副本，互不干扰。用于传递请求上下文 |
| **Mojibake（乱码）** | 用错误的字符编码解读字节，产生无意义字符。如 GBK 字节被按 Latin-1 解读 |
| **IVFFlat** | PgVector 的一种向量索引类型。通过聚类加速近似最近邻搜索 |
| **Top-K** | 检索时返回最相似的前 K 个结果。K 越大召回越多，但也可能引入噪声 |
| **Similarity Threshold（相似度阈值）** | 检索结果的最低分数门槛。低于此分数的结果被丢弃 |
| **滑动窗口** | 记忆管理策略。只保留最近 N 轮对话，丢弃更早的，控制上下文长度 |

---

## 十二、代码阅读指南

### 12.1 推荐阅读顺序

如果你想理解 AgentOne 的代码，建议按以下顺序：

**第一步：从对话入口开始**
```
ChatServiceImpl.chatStream()
  → 这是对话的完整编排，可以看到从接收消息到返回响应的全流程
```

**第二步：理解系统提示词是怎么组装的**
```
ChatServiceImpl.buildSystemPrompt()
  → 四段拼接：AGENTS.md + Skill 描述 + RAG 检索结果 + 历史对话
```

**第三步：理解 AI 引擎是怎么构建的**
```
ChatServiceImpl.buildReActAgent()
  → 三级模型解析链 + AgentScope ReActAgent 构建
```

**第四步：理解 RAG 是怎么工作的**
```
ChatServiceImpl.buildRagContext()          → 检索 + 过滤 + 排序 + 预算注入
KnowledgeServiceImpl.search()              → 知识库检索入口
VectorStoreService.searchWithProvider()    → 向量相似度搜索
```

**第五步：理解文档是怎么处理的**
```
KnowledgeServiceImpl.uploadDocument()      → 同步解析 + 触发异步
DocumentProcessor.processAsync()           → 异步分块 + 向量化
ChunkService.splitByStrategy()             → 三种分块策略
VectorStoreService.saveBatchWithProvider() → 批量向量化存储
```

**第六步：理解多租户是怎么隔离的**
```
WorkspaceInterceptor                       → MyBatis 拦截器自动注入 workspace_id
AsyncConfig (TaskDecorator)                → 异步线程上下文传播
```

### 12.2 关键文件速查

| 文件 | 职责 |
|------|------|
| `ChatServiceImpl.java` | 对话核心编排（chat/chatStream/buildSystemPrompt/buildReActAgent/buildRagContext） |
| `AgentServiceImpl.java` | Agent CRUD + 状态机 + 级联删除 |
| `KnowledgeServiceImpl.java` | 知识库业务编排（CRUD/上传/重试/删除/检索/绑定/计数） |
| `DocumentProcessor.java` | 异步分块 + 向量化（独立类以保证 @Async 代理生效） |
| `DocumentParser.java` | Tika 文档解析（跳过嵌入资源） |
| `ChunkService.java` | 三种分块策略实现 |
| `VectorStoreService.java` | 向量化 + 维度隔离表管理 + 检索 |
| `WorkspaceInterceptor.java` | 多租户 SQL 自动注入 |
| `JwtAuthFilter.java` | JWT 认证过滤器 |
| `ApiKeyAuthFilter.java` | API Key 认证 + 限流 |

---

## 附录：API 接口速查

> 完整 API 文档见 `docs/api/rest-api.md`（~65 个端点 + 错误码表），SSE 协议见 `docs/api/sse-protocol.md`。

### 认证 `/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册（邮箱+密码，无需认证） |
| POST | `/api/auth/login` | 登录（5 次失败锁定） |
| POST | `/api/auth/switch-workspace/{id}` | 切换工作空间（返回新 Token） |

### Agent `/api/agents`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents` | Agent 列表 |
| POST | `/api/agents` | 创建 Agent |
| PUT | `/api/agents/{id}` | 更新（部分更新，全字段可选） |
| DELETE | `/api/agents/{id}` | 物理删除 + 级联清理 |
| POST | `/api/agents/{id}/publish` | 发布 |
| POST | `/api/agents/{id}/stop` | 停用 |
| POST | `/api/agents/{id}/revert` | 退回草稿 |

### 对话 `/api/chat`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步对话 |
| POST | `/api/chat/stream` | 流式对话（SSE） |
| GET | `/api/chat/sessions?agentId=` | 会话列表 |
| GET | `/api/chat/sessions/{id}/messages` | 消息列表 |

### 知识库 `/api/knowledge`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/bases` | 创建知识库 |
| POST | `/api/knowledge/bases/{id}/documents` | 上传文档（multipart） |
| POST | `/api/knowledge/documents/{id}/retry` | 重试失败文档 |
| POST | `/api/knowledge/bases/{id}/search` | 向量检索 |
| POST | `/api/knowledge/bindings` | 绑定知识库到 Agent |
| PUT | `/api/knowledge/bindings/{id}` | 更新绑定参数（topK/阈值） |

### 模型 `/api/model-providers` + `/api/models`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/model-providers` | 创建供应商 |
| POST | `/api/model-providers/{id}/check` | 连通性检测 |
| POST | `/api/models/providers/{id}/models` | 创建模型 |

### 开放接口 `/v1`

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/v1/chat` | X-API-Key | 同步对话 |
| POST | `/v1/chat/stream` | X-API-Key | 流式对话 |
| GET | `/v1/health` | 无 | 健康检查 |

### 错误码速查

| 码 | 含义 |
|----|------|
| 0 | 成功 |
| 400 | 参数校验失败 |
| 401 | 未认证 / API Key 无效 |
| 1001 | 邮箱已注册 |
| 1003 | 账号已锁定 |
| 3002 | 已发布 Agent 不能直接修改 |
| 3003 | Agent 已停用 |
| 6008 | Embedding 模型配置缺失 |
| 6009 | 不支持的文件类型 |
| 6010 | 重复文档 |
| 6011 | Chat 模型供应商不存在 |

---

> **文档版本**: v2.0（2026-07-21 重写）
> **适用阶段**: Phase 1 MVP
> **配套文档**: 架构 `01-architecture.md` / Agent 引擎 `02-agent-engine.md` / RAG `03-rag-knowledge.md` / 改进清单 `improvements.md` / REST API `rest-api.md` / SSE 协议 `sse-protocol.md`
