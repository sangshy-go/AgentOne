# 知识库 RAG 技术方案

## 1. 概述

知识库模块（`agentone-knowledge`）覆盖：文档上传 → 解析 → 分块 → 向量化 → PgVector 存储 → 向量检索 → Agent 绑定注入。

**一句话评估**（详见 `improvements.md` 全量审查）：工程骨架（异步 / 租户 / 事务 / 重试 / 失败可恢复）扎实；检索质量栈（解析→分块→检索→重排→评测）为 MVP 水平，支撑「人在环、答错可接受」的内部知识助手场景。

## 2. 处理链路

```
上传文件
  → [同步, 请求线程] DocumentParser(Tika) 解析为纯文本（跳过嵌入资源）
  → [同步] 落库 document.raw_content（供失败重试）+ 类型白名单/重复检测校验
  → [异步, doc-process 线程池] DocumentProcessor.processAsync
       → ChunkService 按策略分块
       → injectHeadingContext 注入标题上下文
       → 分块写入 document_chunk
       → 分批(20条/批) 调外部 Embedding API → 写入 vector_store_{dim}
       → document.status=ready；docCount/chunkCount 从 DB 重算（recomputeKbCounts）
检索
  → KnowledgeServiceImpl.search(knowledgeId, query, topK)
      → selectById 校验知识库归属（租户拦截器兜底）
      → resolveEmbeddingModel / resolveProvider（embeddingModelId → model → provider）
      → VectorStoreService.searchWithProvider：余弦相似度 Top-K（按 knowledgeId 过滤）
      → SearchResultVO 列表（不过滤分数，原样返回）
对话注入
  → ChatServiceImpl.buildRagContext（见 §5）
```

## 3. 文档解析（DocumentParser）

- **Apache Tika `AutoDetectParser`**，支持 pdf/docx/doc/md/txt/csv/html。
- **跳过嵌入资源**（`EmbeddedDocumentExtractor.shouldParseEmbedded → false`）：Tika 默认递归解析 docx 内嵌缩略图/OLE 对象，曾导致图内 GBK 文本被按 Latin-1 误读成乱码混入正文（Day 11 修复）。知识库只需索引文档正文。
- 后端类型白名单强校验（错误码 6009）；同库「文件名+大小」重复检测（6010）。
- 原文存 `document.raw_content`（TEXT），重试复用原文不重新解析——**解析层失败需删除重传**。

## 4. 分块策略（ChunkService）

| 策略 | 实现 | 适用 |
|------|------|------|
| `by-length`（默认） | Spring AI `TokenTextSplitter`（**按 token** 计） | 通用 |
| `by-title` | 自研，按 Markdown 标题切分 | 结构化 MD 文档 |
| `by-paragraph` | 自研，按段落切分（字符≈token×3 估算） | 连续正文 |

- 参数（`knowledge_base` 级配置，V11 迁移引入）：`chunkSize`（默认 400，**token 计**）、`chunkOverlap`（默认 60，字符级后处理拼接）。
- `injectHeadingContext`：chunk 首行为 Markdown 标题时，把标题层级路径注入块首，增强检索语义（对 PDF 基本不生效）。

## 5. 向量化与存储（VectorStoreService）

- **Embedding**：Spring AI `OpenAiEmbeddingModel`，Provider 模式动态构建（知识库 `embeddingModelId` → `model` 表 → `model_provider` 的 apiKey/baseUrl；缺失报 6008）。维度由系统首次使用时自动探测写入 `model.dimensions`。
- **存储**：PgVector，**按维度隔离表** `vector_store_{dim}`（如 1024 维 → `vector_store_1024`），IVFFlat 索引（cosine, lists=100），metadata 存 documentId/knowledgeId/chunkIndex。
- id 列为 TEXT（V6 迁移修复 Spring AI 默认 UUID 与 MyBatis-Plus 32 位 hex 不兼容问题）。
- 异步线程池：core=2 / max=4 / queue=100 / CallerRunsPolicy，`TaskDecorator` 传播租户上下文。

## 6. 检索与对话注入

`ChatServiceImpl.buildRagContext(agentId, sessionId)`：

```
1. listBindings(agentId) 取全部知识库绑定
2. query = 最后一条用户消息（getLastUserMessage）
3. 逐知识库 search(topK) → 按各 binding.similarityThreshold（默认 0.7，前端可调）过滤
4. 全局按 score 降序混排（跨知识库）
5. RAG_MAX_TOKENS=3000 预算内截断
6. 拼入系统提示词（要求模型基于上下文回答、注明来源文档名）
```

- 阈值与 topK 通过 `PUT /api/knowledge/bindings/{id}` 可调（前端「能力绑定」已暴露入口）。
- 手动检索面板（`POST /bases/{id}/search`）**不过滤分数**，与 Agent 端阈值过滤行为不同（调试时注意）。

## 7. 数据库设计

| 表 | 关键字段 |
|----|---------|
| `knowledge_base` | name / description / embeddingModelId / chunkStrategy / chunkSize / chunkOverlap / docCount / chunkCount / workspace_id |
| `document` | knowledgeId / name / type / size / status(pending/processing/ready/error) / errorMsg / rawContent / chunkCount |
| `document_chunk` | documentId / knowledgeId / chunkIndex / content / tokenCount |
| `vector_store_{dim}` | id(text) / content / embedding(vector) / metadata(jsonb) —— Spring AI 管理 |
| `agent_knowledge_binding` | agentId / knowledgeId / topK / similarityThreshold |

删除知识库级联清理 document / document_chunk / 向量 / 绑定。

## 8. 关键技术决策

| 决策 | 选择 | 理由 | 备选 |
|------|------|------|------|
| 解析器 | Tika + 跳过嵌入资源 | 全格式通用，治本避免乱码 | POI 专用解析（仅 docx） |
| 分块 | Spring AI TokenTextSplitter 为主 | 框架提供、token 感知（遵循「优先开源库」原则） | 纯自研字符切分 |
| 向量库 | PgVector 按维度分表 | 与业务同库零运维；换 Embedding 模型时新旧维度互不污染 | 单一 vector_store 表 |
| 失败恢复 | raw_content 落库 + retry | 向量化失败（网络抖动）无需重传 | 全量重传 |
| 计数维护 | DB 重算（recomputeKbCounts） | 消除应用层 +/- 的并发漂移（E10 修复） | 应用层累加 |

## 9. 已知限制与演进方向

完整 68 项审查清单见 `docs/technical/improvements.md`「A1. 知识库（RAG）」（细节与现状基线见 `improvement-details.md`）。核心限制：

| 方向 | 现状 | 演进（Phase 2+） |
|------|------|------------------|
| 检索质量 | 纯向量、无重排、无查询改写、阈值 0.7 未校准 | 混合检索（PG 全文）→ rerank → 评测体系（Recall@K） |
| 解析 | PDF 表格被拍平、无 OCR | 表格结构解析、扫描件 OCR |
| 可观测 | 仅四态状态、无检索质量监控 | 处理进度、命中率/零结果率监控 |
| 测试 | 模块零单测 | ChunkService / 检索集成测试 |

## 10. 代码阅读建议

1. `KnowledgeServiceImpl.uploadDocument` → `DocumentProcessor.processAsync`（处理链路）；
2. `ChunkService.splitByToken / splitByTitle / splitByParagraph`（分块逻辑）；
3. `VectorStoreService.searchWithProvider`（检索）；
4. `ChatServiceImpl.buildRagContext`（注入策略）。
