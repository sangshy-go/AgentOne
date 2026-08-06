<div align="center">

# AgentOne（灵一）

**开源 AI Agent 中台 —— 像搭表单一样构建你的企业级 AI 助手**

完全私有部署 · 数据不出域 · 源码自主可控 · 对话可留痕

*On-premise AI Agent platform for regulated industries — banking, insurance, and state-owned enterprises.*

配置模型 → 建立知识库 → 创建 Agent → 开始对话 → API 接入，五步跑通

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?logo=springboot&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20PgVector-336791?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)

[快速开始](#-快速开始) · [界面预览](#-界面预览) · [系统架构](#-系统架构) · [核心特性](#-核心特性) · [文档](#-文档) · [路线图](#-路线图)

</div>

---

## 🤔 AgentOne 是什么

AgentOne 是一个**开箱即用的 AI Agent 中台**。它把构建 AI 助手所需的模型接入、知识库 RAG、Agent 编排、流式对话、多租户隔离和开放 API 等能力，封装成一套带完整管理后台的产品——不需要写一行代码，在页面上点一点，就能拥有自己的企业级 AI 助手，并通过 API 接入任意业务系统。

**它解决什么问题？** 自己从零搭一个 Agent 应用，要处理模型适配、文档解析、向量检索、流式协议、会话管理、权限隔离……每一件都是坑。AgentOne 把这些沉淀为产品能力，让你把精力放在业务本身。

**适合谁用？**

- **银行 / 保险 / 金融 / 国企**：一键私有部署、数据不出域、源码自主可控的合规向 AI 中台（见 [ 合规与安全](#-合规与安全)）
- 想快速给团队/产品加 AI 能力的**业务团队**（配置即用，API 接入）
- 想系统学习 **Agent / RAG 工程实践**的**开发者**（完整源码 + 深度技术文档）

## ✨ 核心特性

| 能力 | 说明 |
|------|------|
| 🧠 **Agent 引擎** | 基于 AgentScope 的 ReAct 自主决策循环；AGENTS.md 人格定义；SSE 流式对话；思考过程可视化；会话管理与上下文压缩 |
| 📚 **知识库 RAG** | PDF / Word / MD / TXT / CSV 上传 → Tika 解析 → 智能分块（长度/标题/段落）→ 向量化 → PgVector 存储与相似度检索；Agent 可按相似度阈值绑定多个知识库 |
| 🔌 **多模型管理** | 模型供应商与模型两层结构，兼容所有 OpenAI 协议服务（DashScope、DeepSeek、Ollama…）；Chat / Embedding 模型按工作空间配置；一键连通性检测 |
| 🛠 **内置 Skill** | 知识库检索、HTTP 请求、代码执行三种内置技能，Agent 在 ReAct 循环中自主调用 |
| 🔑 **开放 API** | API Key 生命周期管理（生成/停用/限额）；`/v1/chat` 同步与流式对话；`/v1/health` 健康检查，方便接入与运维 |
| 🏢 **多租户安全** | 工作空间级数据隔离；Sa-Token JWT 认证；邮箱注册/登录 + 登录失败限流；BCrypt 密码哈希 |
| 🖥 **完整管理后台** | Vue 3 + Naive UI：Agent 六维配置、对话测试面板、知识库管理、模型管理、API Key 管理，全部可视化 |
| 🐳 **一键部署** | 根目录 `docker compose up` 同时拉起 PostgreSQL、Redis、后端、前端；Flyway 自动建表迁移 |

## 🛡 合规与安全

> 面向银行 / 保险 / 金融 / 国企等高合规行业：**只宣传已交付的能力，增强项如实标注路线图**。

| 你关心的 | AgentOne 的回答 | 状态 |
|---------|----------------|------|
| 数据不能出域 | 全私有部署（源码 / Docker），无强制外联；模型可接内网部署（Ollama 等）或国产大模型（通义 / DeepSeek / GLM 等 OpenAI 兼容协议） | ✅ 已具备 |
| 源码自主可控 | MIT 开源，全链路代码可审计，无供应商黑盒 | ✅ 已具备 |
| 多部门数据隔离 | 工作空间级多租户隔离 + MyBatis-Plus 租户拦截器兜底 | ✅ 已具备 |
| 凭据与账号安全 | 密码 BCrypt 哈希；API Key 仅存 SHA-256 哈希、不留明文；JWT 过期机制；登录失败锁定（阈值/时长可配置） | ✅ 已具备 |
| 对话可留痕 | 对话全量持久化（含 trace_id、Skill 调用快照、耗时），可回溯任意一轮问答的完整上下文 | ✅ 已具备 |
| 企业 IT 可接管 | Java 17 + Spring Boot 企业级技术栈，Flyway 迁移管理；企业科技部门二开、运维、审计代码无门槛 | ✅ 已具备 |
| 细粒度权限 | RBAC 角色权限（管理员 / 开发者 / 运营等） | 🔜 Phase 2 |
| 审计与监控看板 | 调用链审计（`audit_log` / `skill_call_log` 表结构已预留）、对话日志与监控仪表盘 | 🔜 Phase 2 |

欢迎金融同业在 [Issues](https://github.com/sangshy-go/AgentOne/issues) 提出合规要求（等保、审计、信创适配等），优先排进迭代计划。

## 🖼 界面预览

**产品官网 / 登录页**

![AgentOne 官网](./docs/assets/landing.jpg)

**管理后台控制台**

![AgentOne 控制台](./docs/assets/dashboard.png)

## 🏗 系统架构

```
┌────────────────────────────────────────────────────────┐
│  浏览器（Vue 3 + Naive UI 管理后台）                    │
└──────────────┬─────────────────────────────────────────┘
               │ HTTP / SSE
┌──────────────▼─────────────────────────────────────────┐
│  Nginx（静态资源 + 反代 /api、/v1，SSE 长连接不缓冲）    │
└──────────────┬─────────────────────────────────────────┘
               │
┌──────────────▼─────────────────────────────────────────┐
│  Spring Boot 3.4（Java 17，8 个 Maven 模块）            │
│  ┌─────────────────────────────────┬──────────────┐  │
│  │ auth     │ workspace │ agent      │ knowledge    │  │
│  │ 认证/JWT │ 多租户    │ ReAct 引擎 │ RAG 知识库   │  │
│  ├──────────┼───────────┼────────────┼──────────────┤  │
│  │ skill    │ apikey    │ api(启动)  │ common       │  │
│  │ 内置技能 │ 开放 API  │ Flyway     │ 通用组件     │  │
│  └──────────┴───────────┴────────────┴──────────────┘  │
└───────┬──────────────┬───────────────┬─────────────────┘
        │              │               │
┌───────▼──────┐ ┌─────▼─────┐ ┌───────▼───────┐
│ PostgreSQL16 │ │  Redis 7  │ │ 外部 LLM API  │
│ + PgVector   │ │ 会话/限流 │ │ (OpenAI 兼容) │
└──────────────┘ └───────────┘ └───────────────┘
```

**一次对话的完整数据流**：

```
用户输入 → Nginx → ChatController（参数校验）
  → ChatService（加载会话上下文）
  → AgentEngine（ReAct 循环）
      ├→ 模型解析链：Agent 指定模型 → 工作空间默认 → 全局回退
      ├→ LLM 调用 → 思考 / 决策
      ├→ Skill 调用 → 知识库检索 / HTTP / 代码执行
      └→ 上下文管理 → 超长自动压缩
  → SSE 流式推送（session / thinking / delta / done / error 五种事件）
```

## 🚀 快速开始

**前置要求**：Docker Desktop（或 Docker Engine + Compose v2）。首次构建约 10–20 分钟（取决于网络）。

```bash
git clone https://github.com/sangshy-go/AgentOne.git && cd AgentOne

# 可选：自定义端口 / 凭据 / 全局默认模型（不创建也可直接启动，全部有默认值）
cp .env.example .env

# 一键启动：PostgreSQL + Redis + 后端 + 前端
docker compose up -d --build

# 观察后端启动（Flyway 建表 + 服务就绪，约 1–2 分钟）
docker compose logs -f backend
```

启动完成后：

| 入口 | 地址 | 说明 |
|------|------|------|
| 管理后台 | http://localhost | 注册账号后即可使用（端口见 `.env` 的 `WEB_PORT`） |
| 后端 API | http://localhost:8080 | 控制台 API 与开放 API |
| 健康检查 | http://localhost:8080/v1/health | `data.status` 为 `"UP"` 表示就绪 |

**首次使用五步走**：

1. 打开管理后台 → 注册账号（自动创建默认工作空间）
2. 「模型管理」→ 添加模型供应商（任意 OpenAI 兼容 API，如 DashScope）→ 添加 Chat 与 Embedding 模型 → 点击检测
3. 「知识库」→ 创建知识库（选 Embedding 模型）→ 上传文档，等待状态变为 `ready`
4. 「Agent」→ 创建 Agent → 在「模型策略」选 Chat 模型、「能力绑定」勾选知识库 → 发布
5. 详情页点击「测试对话」开始聊天；「API Key」页创建 Key 后即可程序化接入

**程序化接入示例**：

```bash
curl -N http://localhost:8080/v1/chat \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"agentId": "<AGENT_ID>", "message": "你好，介绍一下你自己", "stream": true}'
```

### 端到端验证脚本

仓库自带 E2E 冒烟脚本，部署后可立即验证全链路：

```bash
# 基础冒烟（无需 LLM Key，验证注册/工作空间/知识库/鉴权链路）
./scripts/e2e-demo.sh

# 完整闭环（含流式对话 + RAG 命中验证 + 开放 API）
API_KEY=sk-xxx ./scripts/e2e-demo.sh
# 可选: BASE=http://localhost:8090 CHAT_MODEL=qwen-plus EMBED_MODEL=text-embedding-v3
```

## 💻 本地开发

```bash
# 1. 启动基础设施并构建后端（:8080）
cd agentone-server && ./start.sh

# 2. 启动前端开发服务器（:3000，已配置 /api 代理）
cd agentone-web && npm install && npm run dev
```

> 本地开发的基础设施编排在 `agentone-server/docker/docker-compose.yml`（PostgreSQL / Redis / MinIO），与根目录的全栈部署编排互不影响。

**技术栈**：Spring Boot 3.4 · Java 17 · AgentScope · Spring AI · MyBatis-Plus · Sa-Token · PgVector · Apache Tika · Flyway · Vue 3 · Vite · Naive UI · Pinia

## ⚙️ 配置说明

全部通过根目录 `.env` 覆盖（均有默认值，不创建 `.env` 也可直接启动）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `WEB_PORT` | `80` | 前端（Nginx）端口 |
| `API_PORT` | `8080` | 后端 API 端口 |
| `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `5432` / `agentone` / `agentone` / `agentone123` | 数据库 |
| `REDIS_PORT` / `REDIS_PASSWORD` | `6379` / `agentone123` | Redis |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `AGENT_MODEL` | 占位值 | 全局默认模型（Agent 未单独指定 Chat 模型时的回退；推荐在页面上按工作空间配置） |
| `JWT_SECRET` | 开发用默认值 | **生产环境务必修改** |

后端基于 Spring Boot 外部化配置，任意 `spring.*` 属性均可用环境变量覆盖（如 `SPRING_DATASOURCE_URL`）。

## 📁 目录结构

```
├── docker-compose.yml        # 全栈一键部署（基础设施 + 后端 + 前端）
├── .env.example              # 部署环境变量示例
├── LICENSE                   # MIT License
├── scripts/
│   └── e2e-demo.sh           # 端到端演示 / 冒烟测试脚本
├── agentone-server/          # 后端（Spring Boot 3.4 多模块）
│   ├── agentone-api/         # 启动模块 + Flyway 迁移
│   ├── agentone-auth/        # 认证（Sa-Token JWT + 登录限制）
│   ├── agentone-workspace/   # 工作空间多租户
│   ├── agentone-agent/       # Agent 引擎（ReAct + SSE + 模型供应商）
│   ├── agentone-knowledge/   # 知识库 RAG（Tika + Spring AI + PgVector）
│   ├── agentone-skill/       # Skill 框架（知识库检索/HTTP/代码执行）
│   ├── agentone-apikey/      # API Key + /v1 开放接口 + 健康检查
│   ├── agentone-common/      # 统一响应/异常/租户拦截器
│   ├── docker/               # 本地开发基础设施编排
│   └── start.sh              # 本地一键启动脚本
├── agentone-web/             # 前端（Vue 3 + Vite + Naive UI）
│   ├── src/views/            # 页面：agents/knowledge/models/api-keys/settings
│   └── nginx.conf            # 部署用 Nginx 配置（SPA + SSE 反代）
└── docs/
    ├── technical/            # 技术方案文档（架构/Agent 引擎/RAG）
    ├── api/                  # API 文档（REST + SSE 协议）
    └── phase-1-summary.md    # Phase 1 综合技术总结
```

## 📖 文档

| 文档 | 内容 |
|------|------|
| [docs/technical/01-architecture.md](./docs/technical/01-architecture.md) | 系统架构：模块划分、分层设计、数据流、部署拓扑 |
| [docs/technical/02-agent-engine.md](./docs/technical/02-agent-engine.md) | Agent 引擎：ReAct 循环、模型解析链、SSE 事件、Skill 调用 |
| [docs/technical/03-rag-knowledge.md](./docs/technical/03-rag-knowledge.md) | 知识库 RAG：解析 / 分块 / 向量化 / 检索 / 注入全链路 |
| [docs/api/rest-api.md](./docs/api/rest-api.md) | REST API 参考（约 65 个端点 + 错误码表） |
| [docs/api/sse-protocol.md](./docs/api/sse-protocol.md) | SSE 流式对话协议（5 种事件） |
| [docs/phase-1-summary.md](./docs/phase-1-summary.md) | Phase 1 综合技术文档：功能详解、踩坑记录、关键决策（面向初学者） |

## ❓ 常见问题

**Q：端口被占用怎么办？**
修改 `.env` 中的 `WEB_PORT` / `API_PORT` 等，然后 `docker compose up -d`。

**Q：对话报 "HTTP connect timed out"？**
Agent 未指定 Chat 模型时会回退到全局默认（`api.openai.com`，国内不可达）。请在「模型策略」Tab 为该 Agent 选择已配置的 Chat 模型，或在 `.env` 中设置 `OPENAI_BASE_URL` / `OPENAI_API_KEY`。

**Q：上传文档后一直 "处理中" 或变 error？**
详情页查看错误信息。常见原因是 Embedding 模型不可用（供应商 Key/URL 错误），请先到「模型管理」检测 Embedding 模型连通性。注意：失败的旧文档修复配置后需删除重传。

**Q：如何重置所有数据？**
`docker compose down -v`（会删除数据库、Redis 全部数据），再重新 `up`。

## 🗺 路线图

- ✅ **Phase 1（MVP，已完成）**：Agent 引擎（ReAct + SSE）· 知识库 RAG · 模型管理 · 内置 Skill · 开放 API · 完整管理后台 · Docker 一键部署
- 🔜 **Phase 2（进行中）**：Skill 中心（技能市场 / 安装 / 调试器）· MCP 集成 · IM Bot 网关（企微 / 钉钉）· 监控与审计看板 · RBAC 细粒度权限

欢迎在 [Issues](https://github.com/sangshy-go/AgentOne/issues) 提出你最需要的能力。

**分支策略（便于学习每个阶段的演进）**：

| 分支 | 内容 |
|------|------|
| `main` | 最新稳定版（各阶段完成并验证后合入） |
| `phase-1` | Phase 1 MVP 冻结快照，不再改动 |
| `phase-2` / `phase-3` | 对应阶段的完整开发历史，从上一阶段终点拉出 |

想看某个阶段"到底加了什么"，对比相邻分支即可，例如 `git diff phase-1..phase-2`。

## 🤝 贡献

欢迎任何形式的贡献：

- **报 Bug / 提需求**：提交 [Issue](https://github.com/sangshy-go/AgentOne/issues)，附上复现步骤或场景描述
- **提交代码**：Fork → 创建特性分支 → 本地跑通 `scripts/e2e-demo.sh` → 提交 PR，描述清楚动机与改动
- **完善文档**：错别字、表述不清、缺失的说明，都欢迎直接 PR

## 📄 License

[MIT](./LICENSE) —— 可自由使用、修改、商用，保留版权声明即可。

---

<div align="center">

如果这个项目对你有帮助，欢迎点个 Star ⭐ 支持一下。

</div>
