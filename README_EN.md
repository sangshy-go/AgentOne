<div align="center">

<img src="./agentone-web/public/agentone-luminous-lockup.png" alt="AgentOne" width="420">

# AgentOne

**Open-source, self-hosted AI Agent platform for regulated industries**

Fully on-premise · Data never leaves your network · Full source code · Complete conversation audit trail

[简体中文](./README.md) | **English**

Configure models → Build knowledge bases → Create Agents → Chat → Integrate via API. Live in five steps.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?logo=springboot&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20PgVector-336791?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)

</div>

---

## 🤔 What is AgentOne

AgentOne is a ready-to-use AI Agent platform. It packages everything an AI assistant needs — model integration, RAG knowledge bases, Agent orchestration, a Skill ecosystem, MCP integration, IM bots, streaming chat, multi-tenancy, and an open API — into a single product with a full admin console. No code required: build your own enterprise AI assistant by clicking through a few pages, then plug it into any business system via API or IM bot.

**Built for regulated environments** — banking, insurance, finance, and state-owned enterprises:

- **100% on-premise**: deploy via Docker or from source; no mandatory outbound calls; models can run fully internal (Ollama, etc.)
- **Auditable by design**: every conversation is persisted (with `trace_id` and tool-call snapshots); write operations are audit-logged
- **Enterprise-grade stack**: Java 17 + Spring Boot — easy for in-house IT teams to review, operate, and extend

## ✨ Key Features

| Capability | Description |
|------|------|
| 🧠 **Agent Engine** | ReAct reasoning loop (AgentScope-based); AGENTS.md persona definition; SSE streaming; visible thinking & tool calls; context compression; two-person approval for publishing |
| 📚 **Knowledge Base RAG** | PDF / Word / MD / TXT / CSV upload → Tika parsing → smart chunking → embedding → PgVector similarity search; Agents bind multiple KBs with similarity thresholds |
| 🔌 **Multi-Model Management** | Two-layer provider/model structure; works with any OpenAI-compatible endpoint (DeepSeek, Ollama, DashScope…); per-workspace Chat/Embedding config; one-click connectivity check |
| 🧩 **Skill Ecosystem** | Built-in skills (KB search / HTTP / code execution) + skill marketplace (install / import / export) + three-step debugger; Agents invoke skills autonomously in the ReAct loop; two-phase confirmation for dangerous actions |
| 🔗 **MCP Integration** | Connect external MCP Servers via stdio / SSE / HTTP; service discovery & tool registration; MCP tools mount alongside Skills |
| 💬 **IM Bot Gateway** | Native DingTalk bot (@-mention callback + signed outbound); AES-encrypted credentials; multi-turn session mapping; WeCom / Feishu on the roadmap |
| 🔑 **Open API** | API Key lifecycle management (issue / revoke / quota); `/v1/chat` sync & streaming; `/v1/health` health check |
| 🏢 **Enterprise Security** | Workspace-level data isolation; RBAC with 4 roles (owner / developer / operator / auditor); automatic audit logging; Sa-Token JWT; login rate limiting; BCrypt password hashing |
| 📊 **Monitoring** | Dashboard; full conversation logs with trace IDs; Skill / MCP call-chain tracing |
| 🐳 **One-command Deploy** | `docker compose up` brings up PostgreSQL, Redis, backend, and frontend; Flyway handles all migrations |

## 🖼 Screenshots

**Chat — streaming answers, file uploads, structured analysis**

![Chat](./docs/assets/chat.png)

**Agent configuration — six dimensions + capability binding (Skills / MCP tools / KBs)**

![Agent config](./docs/assets/agent-config.png)

**Knowledge base RAG — parsing, chunking, embedding, retrieval testing**

![Knowledge base](./docs/assets/knowledge.png)

**Monitoring & audit — conversation logs, call chains, audit trail**

![Monitoring](./docs/assets/monitor-audit.png)

## 🚀 Quick Start

**Prerequisites**: Docker Desktop (or Docker Engine + Compose v2). First build takes ~10–20 minutes.

```bash
git clone https://github.com/sangshy-go/AgentOne.git && cd AgentOne

# Optional: customize ports / credentials / default model (all values have defaults)
cp .env.example .env

# One command: PostgreSQL + Redis + backend + frontend
docker compose up -d --build

# Watch the backend come up (Flyway migrations + service ready, ~1–2 min)
docker compose logs -f backend
```

Once running:

| Entry | URL | Notes |
|------|------|------|
| Admin console | http://localhost | Register an account to start (port: `WEB_PORT` in `.env`) |
| Backend API | http://localhost:8080 | Console API + open API |
| Health check | http://localhost:8080/v1/health | `data.status == "UP"` means ready |

**First-run walkthrough**: register → add a model provider (any OpenAI-compatible API) → create a knowledge base and upload documents → create an Agent, bind the Chat model and KB, hit "Test chat" → submit for two-person publish approval → chat via the console, an IM bot, or the open API.

```bash
curl -N http://localhost:8080/v1/chat \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: Application/json" \
  -d '{"agentId": "<AGENT_ID>", "message": "Hello!", "stream": true}'
```

A built-in E2E smoke script verifies the full chain after deployment:

```bash
./scripts/e2e-demo.sh            # basics (no LLM key needed)
API_KEY=sk-xxx ./scripts/e2e-demo.sh   # full loop: streaming chat + RAG hit + open API
```

## 📖 Documentation

The full technical documentation is maintained in Chinese (see [README.md](./README.md)): system architecture, Agent engine internals, RAG pipeline, Skill system, RBAC & audit, MCP integration, IM gateway, 100+ REST endpoints, and the SSE protocol — plus phase-by-phase implementation notes, ideal if you want to learn how a production Agent platform is built end to end.

**Tech stack**: Spring Boot 3.4 · Java 17 · AgentScope · Spring AI · MyBatis-Plus · Sa-Token · PgVector · Apache Tika · Flyway · Vue 3 · Vite · Naive UI

## 🗺 Roadmap

- ✅ **Phase 1 (MVP)**: Agent engine (ReAct + SSE) · RAG · model management · built-in Skills · open API · admin console · Docker deployment
- ✅ **Phase 2**: Skill ecosystem · MCP integration · IM gateway (DingTalk) · monitoring & audit · RBAC · publish approval
- 🔜 **Phase 3**: retrieval quality (hybrid search / rerank / eval) · admin OpenAPI · WeCom / Feishu bots

Issues and feature requests are very welcome.

## 🤝 Contributing

- **Bug reports / feature requests**: open an [Issue](https://github.com/sangshy-go/AgentOne/issues)
- **Code**: Fork → feature branch → run `scripts/e2e-demo.sh` locally → open a PR
- **Docs**: typo fixes and clarifications are always welcome

## 📄 License

[MIT](./LICENSE) — free to use, modify, and distribute commercially.

---

<div align="center">

If AgentOne is useful to you, a Star ⭐ is greatly appreciated.

</div>
